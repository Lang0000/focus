package com.focus.tracker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * The pill: app icon, today's time in that app and, when [badge] gives one, the
 * Reels/Shorts count. [windowType] is the window it lives in — an accessibility
 * overlay (full build) or an app overlay that needs "display over other apps"
 * (lite build). The user can drag it; the spot is remembered.
 */
class Overlay(
    private val ctx: Context,
    private val windowType: Int,
    /** Today's seconds in an app. */
    private val seconds: (String) -> Int,
    private val badge: (String) -> Badge? = { null },
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var pill: LinearLayout? = null
    private var pillIcon: ImageView? = null
    private var pillTime: TextView? = null
    private var pillSep: View? = null
    private var pillCount: TextView? = null
    private var pillPkg: String? = null
    private var seenResets = -1

    private val iconCache = HashMap<String, Drawable?>()

    fun show(pkg: String) {
        if (pill == null) build()
        if (pillPkg != pkg) {
            pillPkg = pkg
            pillIcon?.setImageDrawable(icon(pkg))
        }
        update()
        pill?.let {
            if (it.visibility != View.VISIBLE) {
                it.alpha = 0f
                it.visibility = View.VISIBLE
                it.animate().alpha(1f).setDuration(180).start()
            }
        }
    }

    /** Redraw the time and badge for the app the pill is showing. */
    fun update() {
        val pkg = pillPkg ?: return
        if (pill == null) return
        pillTime?.text = Format.clock(seconds(pkg))
        val b = badge(pkg)
        val vis = if (b != null) View.VISIBLE else View.GONE
        pillSep?.visibility = vis
        pillCount?.visibility = vis
        if (b != null) {
            pillCount?.text = b.text
            pillCount?.setTextColor(b.color)
        }
    }

    fun hide() {
        val p = pill ?: return
        if (p.visibility == View.VISIBLE) p.visibility = View.GONE
    }

    fun remove() {
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
    }

    /** The user asked to put the pill back: drop it so it is rebuilt in the default spot. */
    fun applyReset() {
        val resets = Store.pillResets(ctx)
        if (seenResets == resets) return
        if (seenResets != -1) remove()
        seenResets = resets
    }

    private fun icon(pkg: String): Drawable? = iconCache.getOrPut(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    private fun build() {
        val icon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val time = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            letterSpacing = 0.02f
        }
        val sep = View(ctx).apply { setBackgroundColor(Color.parseColor("#33FFFFFF")) }
        val count = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
        }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(13), ctx.dp(6))
            background = GradientDrawable().apply {
                cornerRadius = ctx.dp(100).toFloat()
                setColor(Color.parseColor("#EE16181C"))
                setStroke(ctx.dp(1), Color.parseColor("#22FFFFFF"))
            }
            elevation = ctx.dp(4).toFloat()
            addView(icon, LinearLayout.LayoutParams(ctx.dp(20), ctx.dp(20)).apply { rightMargin = ctx.dp(7) })
            addView(time)
            addView(sep, LinearLayout.LayoutParams(ctx.dp(1), ctx.dp(12)).apply {
                leftMargin = ctx.dp(9); rightMargin = ctx.dp(9)
            })
            addView(count)
            visibility = View.GONE
        }

        val saved = Store.pillPos(ctx)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = saved?.first ?: 0
            y = saved?.second ?: ctx.dp(36)
        }

        box.setOnTouchListener(DragListener(params))

        runCatching {
            wm.addView(box, params)
            pill = box
            pillIcon = icon
            pillTime = time
            pillSep = sep
            pillCount = count
            pillPkg = null
        }
    }

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
                    if (abs(dx) > ctx.dp(3) || abs(dy) > ctx.dp(3)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) Store.setPillPos(ctx, params.x, params.y)
                }
            }
            return true
        }
    }
}
