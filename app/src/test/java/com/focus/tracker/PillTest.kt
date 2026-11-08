package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PillTest {
    @Test fun appsWithoutACounterShowNoBadge() {
        assertNull(Pill.badge(Store.INSTAGRAM, count = null, onPlayer = true))
    }

    @Test fun zeroIsHiddenUntilThePlayerIsUp() {
        assertNull(Pill.badge(Store.INSTAGRAM, count = 0, onPlayer = false))
        assertEquals(Badge("▶ 0", Pill.REELS), Pill.badge(Store.INSTAGRAM, count = 0, onPlayer = true))
    }

    @Test fun countIsGreyOffThePlayerAndColouredOnIt() {
        assertEquals(Badge("▶ 12", Pill.IDLE), Pill.badge(Store.YOUTUBE, count = 12, onPlayer = false))
        assertEquals(Badge("▶ 12", Pill.SHORTS), Pill.badge(Store.YOUTUBE, count = 12, onPlayer = true))
        assertEquals(Badge("▶ 3", Pill.REELS), Pill.badge(Store.INSTAGRAM, count = 3, onPlayer = true))
    }
}
