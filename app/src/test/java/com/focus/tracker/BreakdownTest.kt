package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakdownTest {
    private fun use(min: Int, opens: Int = 1) = AppUse(min * 60_000L, opens)

    @Test fun rowsAreTheDaysAppsLongestFirst() {
        val day = mapOf("tg" to use(40), "yt" to use(80), "ig" to use(60))
        assertEquals(listOf("yt", "ig", "tg"), Breakdown.rows(day).map { it.first })
    }

    @Test fun appsUnderAMinuteAreLeftOut() {
        val day = mapOf("yt" to use(5), "calc" to AppUse(59_000, 1))
        assertEquals(listOf("yt"), Breakdown.rows(day).map { it.first })
    }

    @Test fun exactlyAMinuteCounts() {
        assertEquals(listOf("calc"), Breakdown.rows(mapOf("calc" to use(1))).map { it.first })
    }

    @Test fun aTopAppNotUsedThatDayHasNoPart() {
        // Opened for an instant: listed by the system, but zero whole seconds.
        val day = mapOf("yt" to use(1), "ig" to AppUse(400, 1))
        assertEquals(listOf<Pair<String?, Int>>("yt" to 60), Breakdown.bar(day, listOf("yt", "ig")))
    }

    @Test fun topAppsAreTheBiggestOverTheWeek() {
        val week = listOf(
            mapOf("yt" to use(100), "tg" to use(10)),
            mapOf("ig" to use(60), "tg" to use(60)),
        )
        assertEquals(listOf("yt", "tg"), Breakdown.top(week, 2)) // yt 100, tg 70, ig 60
    }

    @Test fun aBarIsTheTopAppsInOrderThenTheRest() {
        val day = mapOf("yt" to use(30), "tg" to use(10), "calc" to use(2), "maps" to use(3))
        assertEquals(
            listOf<Pair<String?, Int>>("yt" to 1800, "tg" to 600, null to 300),
            Breakdown.bar(day, top = listOf("yt", "ig", "tg")),
        )
    }

    @Test fun aBarWithNothingElseHasNoRest() {
        assertEquals(listOf<Pair<String?, Int>>("yt" to 60), Breakdown.bar(mapOf("yt" to use(1)), listOf("yt")))
    }
}
