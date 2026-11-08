package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCardTest {
    private val ig = Store.INSTAGRAM
    private val yt = Store.YOUTUBE

    private fun next(prev: LiveCard?, pkg: String, app: Int, now: Long, count: Int?) =
        LiveCard.next(prev, pkg, secondsToday = app, nowMs = now, count = count)

    @Test fun stopwatchStartsAtTodaysTotal() {
        val card = next(null, ig, app = 600, now = 1_000_000L, count = null)
        assertEquals(400_000L, card.zeroMs) // 1_000_000 - 600 s
    }

    @Test fun sameAppNextSecondIsTheSameCardSoNothingIsReposted() {
        val a = next(null, ig, 600, 1_000_000L, 3)
        val b = next(a, ig, 601, 1_001_000L, 3)
        assertEquals(a, b)
    }

    @Test fun aNewReelChangesTheCardButKeepsTheStopwatch() {
        val a = next(null, ig, 600, 1_000_000L, 3)
        val b = next(a, ig, 605, 1_005_000L, 4)
        assertNotEquals(a, b)
        assertEquals(a.zeroMs, b.zeroMs)
        assertEquals(4, b.count)
    }

    @Test fun switchingAppsRestartsTheStopwatchAtThatAppsTotal() {
        val a = next(null, ig, 600, 1_000_000L, 3)
        val b = next(a, yt, 60, 1_010_000L, 0)
        assertEquals(yt, b.pkg)
        assertEquals(950_000L, b.zeroMs)
    }

    @Test fun stopwatchIsReanchoredWhenItDriftsFromTheStoredTime() {
        // The tracker missed time (process frozen), so stored seconds lag the stopwatch.
        val a = next(null, ig, 600, 1_000_000L, null)
        val b = next(a, ig, 610, 1_060_000L, null)   // 60 s passed, only 10 counted
        assertEquals(1_060_000L - 610_000L, b.zeroMs)
    }

    @Test fun midnightResetsTheStopwatch() {
        val a = next(null, ig, 7200, 10_000_000L, null)
        val b = next(a, ig, 1, 10_001_000L, null)    // new day: total back to 1 s
        assertEquals(10_000_000L, b.zeroMs)
    }

    @Test fun countLabelNamesTheFeed() {
        assertEquals("Reels", next(null, ig, 0, 0L, 1).countLabel)
        assertEquals("Shorts", next(null, yt, 0, 0L, 1).countLabel)
        assertNull(next(null, "org.telegram.messenger", 0, 0L, null).countLabel)
    }

    @Test fun tickJitterDoesNotRepost() {
        // Ticks land a few hundred ms late while stored time is whole seconds.
        val a = next(null, ig, 600, 1_000_000L, null)
        val b = next(a, ig, 601, 1_001_400L, null)
        assertEquals(a, b)
    }

    @Test fun noCounterMeansNoLabelEvenOnInstagram() {
        assertNull(next(null, ig, 0, 0L, null).countLabel)
    }

    // --- The number beside the time -------------------------------------------

    @Test fun feedCountIsShownWhereThereIsOne() {
        val card = LiveCard.next(null, ig, 0, 0L, count = 7, opens = 3)
        assertEquals("Reels" to 7, card.second)
    }

    @Test fun otherwiseTheTimesOpenedToday() {
        val card = LiveCard.next(null, "org.telegram.messenger", 0, 0L, count = null, opens = 14)
        assertEquals("Відкрито" to 14, card.second)
    }

    @Test fun anotherOpenIsANewCard() {
        val a = LiveCard.next(null, "org.telegram.messenger", 600, 1_000_000L, count = null, opens = 2)
        val b = LiveCard.next(a, "org.telegram.messenger", 601, 1_001_000L, count = null, opens = 3)
        assertNotEquals(a, b)
    }
}
