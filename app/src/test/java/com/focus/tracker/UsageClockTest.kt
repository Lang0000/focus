package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageClockTest {
    private val system = HashMap<String, Int>()
    private var asks = 0
    private val clock = UsageClock(refreshMs = 30_000) { pkg -> asks++; system[pkg] ?: 0 }

    @Test fun startsAtTheSystemsTimeForTheApp() {
        system["yt"] = 300
        assertEquals(300, clock.seconds("yt", 1_000))
    }

    @Test fun runsOnBetweenReadsWithoutAskingTheSystem() {
        system["yt"] = 300
        clock.seconds("yt", 1_000)
        assertEquals(312, clock.seconds("yt", 13_500))
        assertEquals(1, asks)
    }

    @Test fun readsTheSystemAgainAfterTheRefreshTime() {
        system["yt"] = 300
        clock.seconds("yt", 1_000)
        system["yt"] = 340
        assertEquals(340, clock.seconds("yt", 31_000))
        assertEquals(2, asks)
    }

    @Test fun anotherAppIsReadFresh() {
        system["yt"] = 300; system["ig"] = 50
        clock.seconds("yt", 1_000)
        assertEquals(50, clock.seconds("ig", 2_000))
    }

    @Test fun afterAPauseTheTimeAwayIsNotCounted() {
        system["yt"] = 300
        clock.seconds("yt", 1_000)
        clock.pause()                                // left for an untracked app
        assertEquals(300, clock.seconds("yt", 20_000)) // read again, not 319
    }
}
