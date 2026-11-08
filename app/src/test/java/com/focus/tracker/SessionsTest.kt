package com.focus.tracker

import com.focus.tracker.Sessions.KEYGUARD
import com.focus.tracker.Sessions.PAUSED
import com.focus.tracker.Sessions.RESUMED
import com.focus.tracker.Sessions.SCREEN_OFF
import com.focus.tracker.Sessions.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionsTest {
    private val yt = "yt"
    private val ig = "ig"
    private fun ev(sec: Long, type: Int, pkg: String, cls: String = "Main") = UsageEvent(sec * 1000, type, pkg, cls)
    private fun tally(vararg e: UsageEvent, from: Long = 0, to: Long = 10_000) =
        Sessions.tally(e.toList(), from * 1000, to * 1000)

    @Test fun resumeToPauseIsTimeInTheApp() {
        val t = tally(ev(10, RESUMED, yt), ev(70, PAUSED, yt))
        assertEquals(AppUse(ms = 60_000, opens = 1), t[yt])
    }

    @Test fun twoVisitsAddUp() {
        val t = tally(ev(10, RESUMED, yt), ev(70, PAUSED, yt), ev(100, RESUMED, yt), ev(130, PAUSED, yt))
        assertEquals(AppUse(90_000, 2), t[yt])
    }

    @Test fun stillOpenCountsUpToTheEndOfTheSpan() {
        val t = tally(ev(10, RESUMED, yt), to = 40)
        assertEquals(AppUse(30_000, 1), t[yt])
    }

    @Test fun aVisitFromBeforeTheSpanCountsOnlyItsPartAndIsNoNewOpen() {
        // Opened before midnight, closed after it.
        val t = tally(ev(50, RESUMED, yt), ev(130, PAUSED, yt), from = 100)
        assertEquals(AppUse(30_000, 0), t[yt])
    }

    @Test fun aVisitThatEndedBeforeTheSpanIsNotCounted() {
        assertNull(tally(ev(10, RESUMED, yt), ev(20, PAUSED, yt), from = 100)[yt])
    }

    @Test fun movingBetweenScreensOfOneAppIsOneVisit() {
        // Android resumes the next activity before it pauses the previous one.
        val t = tally(
            ev(10, RESUMED, yt, "Home"), ev(20, RESUMED, yt, "Player"),
            ev(21, PAUSED, yt, "Home"), ev(60, PAUSED, yt, "Player"),
        )
        assertEquals(AppUse(50_000, 1), t[yt])
    }

    @Test fun anotherAppInFrontEndsTheVisitEvenIfThePauseWasNotLogged() {
        val t = tally(ev(10, RESUMED, yt), ev(40, RESUMED, ig), ev(50, PAUSED, ig))
        assertEquals(AppUse(30_000, 1), t[yt])
        assertEquals(AppUse(10_000, 1), t[ig])
    }

    @Test fun screenOffAndLockEndTheVisit() {
        assertEquals(20_000L, tally(ev(10, RESUMED, yt), ev(30, SCREEN_OFF, "android"))[yt]?.ms)
        assertEquals(20_000L, tally(ev(10, RESUMED, yt), ev(30, KEYGUARD, "android"))[yt]?.ms)
    }

    @Test fun stoppedEndsTheVisitLikePaused() {
        assertEquals(20_000L, tally(ev(10, RESUMED, yt), ev(30, STOPPED, yt))[yt]?.ms)
    }

    @Test fun eventsAfterTheSpanAreIgnored() {
        val t = tally(ev(10, RESUMED, yt), ev(50, PAUSED, yt), ev(60, RESUMED, ig), to = 55)
        assertEquals(40_000L, t[yt]?.ms)
        assertNull(t[ig])
    }

    @Test fun secondsRoundDown() {
        assertEquals(59, AppUse(59_999, 1).seconds)
    }
}
