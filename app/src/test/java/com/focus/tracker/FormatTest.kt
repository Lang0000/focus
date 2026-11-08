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
        assertEquals("1 хв 5 с", Format.words(65))
        assertEquals("12 хв 3 с", Format.words(723))
        assertEquals("1 год 5 хв", Format.words(3900))
    }

    @Test fun opensAreDeclined() {
        assertEquals("1 відкриття", Format.opens(1))
        assertEquals("3 відкриття", Format.opens(3))
        assertEquals("4 відкриття", Format.opens(4))
        assertEquals("5 відкриттів", Format.opens(5))
        assertEquals("11 відкриттів", Format.opens(11))
        assertEquals("14 відкриттів", Format.opens(14))
        assertEquals("21 відкриття", Format.opens(21))
        assertEquals("22 відкриття", Format.opens(22))
        assertEquals("0 відкриттів", Format.opens(0))
    }
}
