package com.focus.tracker

/** The part of the pill after the time: today's Reels/Shorts count. */
data class Badge(val text: String, val color: Int)

object Pill {
    const val IDLE = 0xFFB8BCC4.toInt()
    const val REELS = 0xFFFF7A90.toInt()
    const val SHORTS = 0xFFFF6A55.toInt()

    /**
     * [count] is null for an app that has no Reels/Shorts counter. A zero is
     * shown only while the player is up; the count is coloured while it is.
     */
    fun badge(pkg: String, count: Int?, onPlayer: Boolean): Badge? {
        if (count == null || (count == 0 && !onPlayer)) return null
        val color = when {
            !onPlayer -> IDLE
            pkg == Store.INSTAGRAM -> REELS
            else -> SHORTS
        }
        return Badge("▶ $count", color)
    }
}
