package com.focus.tracker

/**
 * Turns a "roughly every second" ticker into whole seconds of real time. A
 * Handler tick always lands a little late, so adding 1 per tick undercounts;
 * this measures the time between ticks instead and keeps the leftover
 * milliseconds for the next one.
 *
 * [pause] forgets the last tick, so time spent outside a tracked app (or with
 * the screen off) is never counted when tracking resumes.
 */
class SecondClock {
    private var lastMs = -1L
    private var leftoverMs = 0L

    /** Seconds that passed since the previous tick, given the time now in ms. */
    fun tick(nowMs: Long): Int {
        if (lastMs < 0) lastMs = nowMs // first tick only starts the clock
        val step = (nowMs - lastMs).coerceIn(0L, MAX_STEP_SEC * 1000L)
        lastMs = nowMs
        val total = leftoverMs + step
        leftoverMs = total % 1000L
        return (total / 1000L).toInt()
    }

    fun pause() { lastMs = -1L; leftoverMs = 0L }

    companion object {
        /** A tick can never be worth more than this (e.g. after the process was frozen). */
        const val MAX_STEP_SEC = 5
    }
}
