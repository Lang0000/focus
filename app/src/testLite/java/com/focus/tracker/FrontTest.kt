package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrontTest {
    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    @Test fun resumedAppIsInFront() {
        val f = Front()
        assertNull(f.pkg)
        f.on(Front.RESUMED, ig, "Main")
        assertEquals(ig, f.pkg)
    }

    @Test fun switchingAppsFollowsTheNewOne() {
        val f = Front()
        f.on(Front.RESUMED, ig, "Main")
        f.on(Front.PAUSED, ig, "Main")
        f.on(Front.RESUMED, yt, "Watch")
        assertEquals(yt, f.pkg)
    }

    @Test fun pausingTheFrontActivityLeavesNothing() {
        val f = Front()
        f.on(Front.RESUMED, ig, "Main")
        f.on(Front.PAUSED, ig, "Main")
        assertNull(f.pkg)
    }

    @Test fun aLatePauseOfAnotherActivityIsIgnored() {
        val f = Front()
        f.on(Front.RESUMED, ig, "Main")
        f.on(Front.RESUMED, ig, "Reels")
        f.on(Front.PAUSED, ig, "Main")
        assertEquals(ig, f.pkg)
    }

    @Test fun replayingAnOverlapGivesTheSameAnswer() {
        val events = listOf(
            Triple(Front.RESUMED, ig, "Main"), Triple(Front.PAUSED, ig, "Main"),
            Triple(Front.RESUMED, yt, "Watch"),
        )
        val f = Front()
        events.forEach { f.on(it.first, it.second, it.third) }
        events.drop(1).forEach { f.on(it.first, it.second, it.third) }
        assertEquals(yt, f.pkg)
    }

    @Test fun otherEventTypesChangeNothing() {
        val f = Front()
        f.on(Front.RESUMED, ig, "Main")
        f.on(23, ig, "Main") // ACTIVITY_STOPPED
        assertEquals(ig, f.pkg)
    }
}
