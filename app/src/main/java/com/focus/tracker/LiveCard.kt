package com.focus.tracker

import kotlin.math.abs

/**
 * What the live card shows: the app in front, today's time in it as a
 * stopwatch the system runs on its own, and one number beside it - the
 * Reels/Shorts count where the app has one, else how often it was opened today.
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
    /** Times the app was opened today, as the system counts them. */
    val opens: Int,
) {
    /** The number beside the time, with its label. */
    val second: Pair<String, Int>
        get() {
            val label = countLabel
            return if (label != null && count != null) label to count else OPENED to opens
        }

    val countLabel: String?
        get() = when {
            count == null -> null
            pkg == Store.INSTAGRAM -> "Reels"
            pkg == Store.YOUTUBE -> "Shorts"
            else -> null
        }


    companion object {
        const val OPENED = "Відкрито"

        /** How far a stopwatch may run from the stored time before it is reset to it. */
        const val DRIFT_MS = 3000L

        /** The card for [pkg] now, keeping [prev]'s stopwatches where they still agree. */
        fun next(
            prev: LiveCard?, pkg: String, secondsToday: Int, nowMs: Long, count: Int?, opens: Int = 0,
        ): LiveCard = LiveCard(
            pkg = pkg,
            zeroMs = anchor(prev?.zeroMs, secondsToday, nowMs),
            count = count,
            opens = opens,
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
