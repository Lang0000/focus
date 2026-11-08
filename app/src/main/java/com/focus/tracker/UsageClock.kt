package com.focus.tracker

/**
 * Today's time in the app in front, as the system counts it, for the pill and
 * the live card. Reading the system's log costs too much to do every second,
 * so the clock reads it when the app changes or every [refreshMs], and counts
 * on by itself in between - the app is in front all that time.
 *
 * [pause] when no tracked app is in front, so the time away is never counted.
 */
class UsageClock(
    private val refreshMs: Long = 30_000L,
    private val read: (String) -> Int,
) {
    private var pkg: String? = null
    private var base = 0
    private var readAt = 0L

    /** Seconds in [pkg] today, given the monotonic time now in ms. */
    fun seconds(pkg: String, nowMs: Long): Int {
        if (pkg != this.pkg || nowMs - readAt >= refreshMs) {
            this.pkg = pkg
            base = read(pkg)
            readAt = nowMs
        }
        return base + ((nowMs - readAt) / 1000L).toInt()
    }

    fun pause() { pkg = null }
}
