package com.focus.tracker

import kotlin.math.abs

/**
 * What the live card shows: the app in front, today's time in it as a
 * stopwatch the system runs on its own, and the Reels/Shorts count where the
 * app has one.
 *
 * The stopwatch is stored as the elapsedRealtime at which it would have read
 * 0:00 ([zeroMs]). Because the system ticks it, the card only needs reposting
 * when the card itself changes — a new app, a new count, or a re-anchored
 * stopwatch — never once a second.
 */
data class LiveCard(
    val pkg: String,
    val zeroMs: Long,
    /** Reels/Shorts counted today; null for an app without a counter. */
    val count: Int?,
) {
    val countLabel: String?
        get() = when {
            count == null -> null
            pkg == Store.INSTAGRAM -> "Reels"
            pkg == Store.YOUTUBE -> "Shorts"
            else -> null
        }

    /** Accent for the card and the status bar capsule: the app's own colour where we know it. */
    val color: Int
        get() = when (pkg) {
            Store.INSTAGRAM -> INSTAGRAM_PINK
            Store.YOUTUBE -> YOUTUBE_RED
            else -> FOCUS_BLUE
        }

    companion object {
        const val INSTAGRAM_PINK = 0xFFE1306C.toInt()
        const val YOUTUBE_RED = 0xFFFF0033.toInt()
        const val FOCUS_BLUE = 0xFF8FA8FF.toInt()

        /** How far a stopwatch may run from the stored time before it is reset to it. */
        const val DRIFT_MS = 3000L

        /** The card for [pkg] now, keeping [prev]'s stopwatches where they still agree. */
        fun next(
            prev: LiveCard?, pkg: String, secondsToday: Int, nowMs: Long, count: Int?,
        ): LiveCard = LiveCard(
            pkg = pkg,
            zeroMs = anchor(prev?.zeroMs, secondsToday, nowMs),
            count = count,
        )

        /**
         * Keep the [prev] zero point while it agrees with [seconds] to within
         * [DRIFT_MS]; otherwise start the stopwatch at [seconds]. That one rule
         * covers an app switch (another app's total), missed time, and midnight.
         */
        private fun anchor(prev: Long?, seconds: Int, nowMs: Long): Long {
            val fresh = nowMs - seconds * 1000L
            return if (prev != null && abs(prev - fresh) <= DRIFT_MS) prev else fresh
        }
    }
}
