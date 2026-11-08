package com.focus.tracker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * The whole tracker. Once a second it asks which app is in front; if that app is
 * one the user picked, it adds a second to that app and shows the pill on top.
 * In Instagram / YouTube it also looks for the Reels / Shorts player, both on
 * that tick and on content events (so fast swipes are still counted).
 *
 * The pill is a TYPE_ACCESSIBILITY_OVERLAY window, which an accessibility
 * service may draw without the "display over other apps" permission.
 */
class FocusService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager

    // Pill views
    private var pill: LinearLayout? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var pillIcon: ImageView? = null
    private var pillTime: TextView? = null
    private var pillSep: View? = null
    private var pillCount: TextView? = null
    private var pillPkg: String? = null

    // Foreground tracking
    private var lastStatePkg: String? = null
    private var ignored: Set<String> = emptySet()

    // Reels / Shorts
    private enum class Surface { NONE, REEL, SHORT }
    private var surface = Surface.NONE
    private var surfacePkg: String? = null
    private var lastScanAt = 0L
    private val reelDedup = Dedup()
    private val shortDedup = Dedup()

    private val iconCache = HashMap<String, Drawable?>()

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { tick() }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        ignored = buildIgnored()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removePill()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun buildIgnored(): Set<String> {
        val set = hashSetOf(packageName, "com.android.systemui", "android")
        runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.forEach { set.add(it.packageName) }
        }
        return set
    }

    // --- Events ---------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != null && pkg !in ignored) lastStatePkg = pkg
                // React at once instead of waiting for the next tick.
                handler.post { runCatching { refreshPill() } }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                handler.post { runCatching { refreshPill() } }
        }
        if ((pkg == Store.INSTAGRAM || pkg == Store.YOUTUBE) && Store.countReels(this)) {
            val now = System.currentTimeMillis()
            if (now - lastScanAt >= 250L) {
                lastScanAt = now
                runCatching { scan(pkg) }
                updatePillContent()
            }
        }
    }

    /** Which app is in front right now (null when unknown). */
    private fun foreground(): String? {
        runCatching { windows }.getOrNull()?.let { list ->
            for (w in list) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive || w.isFocused)) {
                    val p = w.root?.packageName?.toString()
                    if (p != null && p !in ignored) return p
                }
            }
        }
        rootInActiveWindow?.packageName?.toString()?.let { if (it !in ignored) return it }
        return lastStatePkg
    }

    private fun counting(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked

    // --- Ticker ---------------------------------------------------------------

    private var seenResets = -1

    private fun tick() {
        val resets = Store.pillResets(this)
        if (seenResets != resets) {
            if (seenResets != -1) removePill()
            seenResets = resets
        }
        if (!counting()) { hidePill(); return }
        val fg = foreground()
        if (fg == null || fg !in Store.apps(this)) {
            surface = Surface.NONE
            hidePill()
            return
        }
        Store.addSeconds(this, fg)
        if ((fg == Store.INSTAGRAM || fg == Store.YOUTUBE) && Store.countReels(this)) {
            scan(fg)
            if (surfacePkg == fg) when (surface) {
                Surface.REEL -> Store.addReelSec(this)
                Surface.SHORT -> Store.addShortSec(this)
                Surface.NONE -> {}
            }
        } else {
            surface = Surface.NONE
        }
        if (Store.overlayOn(this)) showPill(fg) else hidePill()
    }

    /** Look for the Reels / Shorts player in [pkg]'s window and count a new item. */
    private fun scan(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val today = Store.today()
        surfacePkg = pkg
        if (pkg == Store.INSTAGRAM) {
            val hit = Detect.reel(root)
            surface = if (hit != null) Surface.REEL else Surface.NONE
            val key = hit?.key
            if (key != null && reelDedup.isNew(key, today)) Store.addReel(this)
        } else {
            val hit = Detect.short(root)
            surface = if (hit != null) Surface.SHORT else Surface.NONE
            val key = hit?.key
            if (key != null && shortDedup.isNew(key, today)) Store.addShort(this)
        }
    }

    private fun refreshPill() {
        if (pill == null && !Store.overlayOn(this)) return
        val fg = foreground()
        if (counting() && fg != null && fg in Store.apps(this) && Store.overlayOn(this)) showPill(fg)
        else hidePill()
    }

    // --- Pill -----------------------------------------------------------------

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun showPill(pkg: String) {
        if (pill == null) buildPill()
        if (pillPkg != pkg) {
            pillPkg = pkg
            pillIcon?.setImageDrawable(icon(pkg))
        }
        updatePillContent()
        pill?.let {
            if (it.visibility != View.VISIBLE) {
                it.alpha = 0f
                it.visibility = View.VISIBLE
                it.animate().alpha(1f).setDuration(180).start()
            }
        }
    }

    private fun updatePillContent() {
        val pkg = pillPkg ?: return
        if (pill == null) return
        pillTime?.text = Store.clock(Store.seconds(this, pkg))
        val count = when {
            !Store.countReels(this) -> -1
            pkg == Store.INSTAGRAM -> Store.reels(this)
            pkg == Store.YOUTUBE -> Store.shorts(this)
            else -> -1
        }
        val showCount = count > 0 || (count == 0 && surface != Surface.NONE && surfacePkg == pkg)
        pillSep?.visibility = if (showCount) View.VISIBLE else View.GONE
        pillCount?.visibility = if (showCount) View.VISIBLE else View.GONE
        if (showCount) {
            pillCount?.text = "▶ $count"
            val live = surface != Surface.NONE && surfacePkg == pkg
            pillCount?.setTextColor(
                if (live) Color.parseColor(if (pkg == Store.INSTAGRAM) "#FF7A90" else "#FF6A55")
                else Color.parseColor("#B8BCC4")
            )
        }
    }

    private fun hidePill() {
        val p = pill ?: return
        if (p.visibility == View.VISIBLE) p.visibility = View.GONE
    }

    private fun removePill() {
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
    }

    private fun icon(pkg: String): Drawable? = iconCache.getOrPut(pkg) {
        runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    private fun buildPill() {
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val time = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            letterSpacing = 0.02f
        }
        val sep = View(this).apply { setBackgroundColor(Color.parseColor("#33FFFFFF")) }
        val count = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(13), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                setColor(Color.parseColor("#EE16181C"))
                setStroke(dp(1), Color.parseColor("#22FFFFFF"))
            }
            elevation = dp(4).toFloat()
            addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(7) })
            addView(time)
            addView(sep, LinearLayout.LayoutParams(dp(1), dp(12)).apply {
                leftMargin = dp(9); rightMargin = dp(9)
            })
            addView(count)
            visibility = View.GONE
        }

        val saved = Store.pillPos(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = saved?.first ?: 0
            y = saved?.second ?: dp(36)
        }

        box.setOnTouchListener(DragListener(params))

        runCatching {
            wm.addView(box, params)
            pill = box
            pillParams = params
            pillIcon = icon
            pillTime = time
            pillSep = sep
            pillCount = count
            pillPkg = null
        }
    }

    /** Lets the user drag the pill anywhere; the spot is remembered. */
    private inner class DragListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(3) || abs(dy) > dp(3)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) Store.setPillPos(this@FocusService, params.x, params.y)
                }
            }
            return true
        }
    }
}
