package com.focus.tracker

import java.util.Locale

object Format {
    /** Clock style for the pill: 4:07, 1:04:07. */
    fun clock(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** Words for the dashboard: 1 год 5 хв, 12 хв 3 с, 40 с. */
    fun words(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "$h год $m хв"
            m > 0 -> "$m хв $s с"
            else -> "$s с"
        }
    }
}
