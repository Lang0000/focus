package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class SecondClockTest {
    @Test fun firstTickOnlyStartsTheClock() {
        val c = SecondClock()
        assertEquals(0, c.tick(10_000L))
    }

    @Test fun lateTicksStillAddUpToRealTime() {
        val c = SecondClock()
        c.tick(0L)
        // A 1s handler that keeps arriving 50ms late: 10 ticks span 10.5s.
        var total = 0
        for (i in 1..10) total += c.tick(i * 1050L)
        assertEquals(10, total)
    }

    @Test fun keepsFractionsAcrossTicks() {
        val c = SecondClock()
        c.tick(0L)
        assertEquals(0, c.tick(600L))
        assertEquals(1, c.tick(1200L))
    }

    @Test fun pauseDropsTheGap() {
        val c = SecondClock()
        c.tick(0L)
        c.tick(1000L)
        c.pause()               // screen off / left the app
        assertEquals(0, c.tick(60_000L))
        assertEquals(1, c.tick(61_000L))
    }

    @Test fun aHugeGapIsCappedSoASleepingPhoneDoesNotCountHours() {
        val c = SecondClock()
        c.tick(0L)
        assertEquals(SecondClock.MAX_STEP_SEC, c.tick(3_600_000L))
    }
}
