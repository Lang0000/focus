package com.focus.tracker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * The whole tracker. Once a second it asks which app is in front; if that app is
 * one the user picked, it adds a second to that app and shows the pill on top.
 * In Instagram / YouTube it also looks for the Reels / Shorts player, both on
 * that tick and on content events (so fast swipes are still counted).
 *
 * Full build only. The pill is a TYPE_ACCESSIBILITY_OVERLAY window, which an
 * accessibility service may draw without the "display over other apps"
 * permission. Play Protect blocks sideloading any APK that declares an
 * accessibility service, which is why the default (lite) build has none.
 */
class FocusService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private lateinit var overlay: Overlay

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

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { tick() }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = Overlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, ::badge)
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        ignored = buildIgnored()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.remove()
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
        if (pkg != null && countsReels(pkg)) {
            val now = System.currentTimeMillis()
            if (now - lastScanAt >= 250L) {
                lastScanAt = now
                runCatching { scan(pkg) }
                overlay.update()
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

    private val clock = SecondClock()

    private fun tick() {
        overlay.applyReset()
        val fg = trackedForeground()
        if (fg == null) {
            clock.pause()
            surface = Surface.NONE
            overlay.hide()
            return
        }
        track(fg, clock.tick(SystemClock.elapsedRealtime()))
        if (Store.overlayOn(this)) overlay.show(fg) else overlay.hide()
    }

    /** The app in front, when it is one we time and the phone is in use. */
    private fun trackedForeground(): String? =
        if (!counting()) null else foreground()?.takeIf { it in Store.apps(this) }

    /** Reels/Shorts are counted only in Instagram/YouTube, only when that app is timed. */
    private fun countsReels(pkg: String) =
        (pkg == Store.INSTAGRAM || pkg == Store.YOUTUBE) &&
            Store.countReels(this) && pkg in Store.apps(this)

    /** Credit [sec] seconds to [fg], and to the Reels/Shorts player when it is up. */
    private fun track(fg: String, sec: Int) {
        if (sec > 0) Store.addSeconds(this, fg, sec)
        if (!countsReels(fg)) { surface = Surface.NONE; return }
        scan(fg)
        if (sec == 0 || surfacePkg != fg) return
        when (surface) {
            Surface.REEL -> Store.addReelSec(this, sec)
            Surface.SHORT -> Store.addShortSec(this, sec)
            Surface.NONE -> {}
        }
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
        val fg = if (Store.overlayOn(this)) trackedForeground() else null
        if (fg != null) overlay.show(fg) else overlay.hide()
    }

    /** Today's Reels/Shorts count for the pill, coloured while the player is up. */
    private fun badge(pkg: String): Badge? {
        val count = when {
            !countsReels(pkg) -> null
            pkg == Store.INSTAGRAM -> Store.reels(this)
            else -> Store.shorts(this)
        }
        return Pill.badge(pkg, count, onPlayer = surface != Surface.NONE && surfacePkg == pkg)
    }
}
