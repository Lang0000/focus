package com.focus.tracker

/**
 * Today's time in the app in front, and how often it was opened, as the system
 * counts them, for the pill and the live card. Reading the system's log costs too much to do every second,
 * so the clock reads it when the app changes or every [refreshMs], and counts
 * on by itself in between - the app is in front all that time.
 *
 * [pause] when no tracked app is in front, so the time away is never counted.
 */
class UsageClock(
    private val refreshMs: Long = 30_000L,
    private val read: (String) -> AppUse,
) {
    private var pkg: String? = null
    private var base = AppUse(0, 0)
    private var readAt = 0L

    /** Seconds in [pkg] today, given the monotonic time now in ms. */
    fun seconds(pkg: String, nowMs: Long): Int {
        if (pkg != this.pkg || nowMs - readAt >= refreshMs) {
            this.pkg = pkg
            base = read(pkg)
            readAt = nowMs
        }
        return base.seconds + ((nowMs - readAt) / 1000L).toInt()
    }

    /** Times [pkg] was opened today, as of the last read (0 before [seconds] read it). */
    fun opens(pkg: String): Int = if (pkg == this.pkg) base.opens else 0

    fun pause() { pkg = null }
}
