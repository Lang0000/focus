package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentTest {
    private val red = 0xFFFF0033.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val clear = 0x00000000
    private fun icon(vararg parts: Pair<Int, Int>) = parts.flatMap { (c, n) -> List(n) { c } }.toIntArray()

    @Test fun theMainColourOfAColourIconWins() {
        // YouTube: a red tile with a white play sign.
        assertEquals(red, Accent.of(icon(red to 70, white to 30)))
    }

    @Test fun aBlackAndWhiteIconHasNoAccent() {
        // Threads: a black glyph on white.
        assertNull(Accent.of(icon(black to 40, white to 60)))
    }

    @Test fun transparentPixelsDoNotCount() {
        assertEquals(red, Accent.of(icon(clear to 2000, red to 100)))   // 5% of all, 100% of opaque
    }

    @Test fun aFewColouredPixelsAreNotAnAccent() {
        // A grey icon with a tiny coloured dot.
        assertNull(Accent.of(icon(0xFF808080.toInt() to 97, red to 3)))
    }

    @Test fun theLargestHueWinsOverASmallerOne() {
        val blue = 0xFF2060FF.toInt()
        assertEquals(blue, Accent.of(icon(blue to 50, red to 20, white to 30)))
    }

    @Test fun theAccentIsTheAverageOfItsHue() {
        // Two close reds average out.
        assertEquals(0xFFF00020.toInt(), Accent.of(icon(0xFFFF0030.toInt() to 50, 0xFFE10010.toInt() to 50)))
    }
}
