package com.focus.tracker

import kotlin.math.abs

/**
 * What the Now Bar card shows: the app in front, a stopwatch the system runs on
 * its own, and the Reels/Shorts count where the app has one.
 *
 * The stopwatch reads today's total for the app: [zeroMs] is the
 * elapsedRealtime at which it would have read 0:00. Because the system ticks it,
 * the card only needs reposting when the card itself changes — a new app, a new
 * count, or a re-anchored stopwatch — never once a second.
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

    companion object {
        /** How far the stopwatch may run from the stored time before it is reset to it. */
        const val DRIFT_MS = 3000L

        /**
         * The card for [pkg] now. Keeps [prev]'s stopwatch while it still agrees
         * with [secondsToday] to within [DRIFT_MS]; otherwise starts it at the
         * stored total. That one rule covers an app switch (another app's total),
         * missed time, and midnight's reset to 0.
         */
        fun next(prev: LiveCard?, pkg: String, secondsToday: Int, nowMs: Long, count: Int?): LiveCard {
            val anchor = nowMs - secondsToday * 1000L
            val zero = if (prev != null && abs(prev.zeroMs - anchor) <= DRIFT_MS) {
                prev.zeroMs
            } else {
                anchor
            }
            return LiveCard(pkg, zero, count)
        }
    }
}
