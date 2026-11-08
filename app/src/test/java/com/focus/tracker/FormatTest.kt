package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun clock() {
        assertEquals("0:00", Format.clock(0))
        assertEquals("0:07", Format.clock(7))
        assertEquals("4:07", Format.clock(247))
        assertEquals("1:04:07", Format.clock(3847))
    }

    @Test fun words() {
        assertEquals("0 с", Format.words(0))
        assertEquals("40 с", Format.words(40))
        assertEquals("12 хв 3 с", Format.words(723))
        assertEquals("1 год 5 хв", Format.words(3900))
    }
}
