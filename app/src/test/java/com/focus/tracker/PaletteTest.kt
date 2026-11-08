package com.focus.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    private val red = 0xFFFF0033.toInt()
    private val blue = 0xFF2060FF.toInt()

    @Test fun anAppKeepsItsIconColour() {
        assertEquals(listOf(red, blue), Palette.pick(listOf(red, blue)))
    }

    @Test fun anAppWithoutAColourGetsTheFirstSpareOne() {
        assertEquals(listOf(red, Palette.SPARE[0]), Palette.pick(listOf(red, null)))
    }

    @Test fun anAppTooCloseToAnEarlierOneGetsASpareColour() {
        // YouTube red, then a slightly different red: the second would be indistinguishable.
        val red2 = 0xFFE81030.toInt()
        assertEquals(listOf(red, Palette.SPARE[0]), Palette.pick(listOf(red, red2)))
    }

    @Test fun spareColoursAreNotRepeated() {
        assertEquals(listOf(Palette.SPARE[0], Palette.SPARE[1]), Palette.pick(listOf(null, null)))
    }

    @Test fun aSpareColourCloseToAnIconColourIsSkipped() {
        val nearSpare0 = Palette.SPARE[0]
        assertEquals(listOf(nearSpare0, Palette.SPARE[1]), Palette.pick(listOf(nearSpare0, null)))
    }

    @Test fun coloursThatDifferOnlyInRedAreApart() {
        val navy = 0xFF000080.toInt(); val purple = 0xFFFF0080.toInt()
        assertEquals(listOf(navy, purple), Palette.pick(listOf(navy, purple)))
    }

    @Test fun pastTheSpareColoursTheyGoRoundAgain() {
        val picked = Palette.pick(List(Palette.SPARE.size + 2) { null })
        assertEquals(Palette.SPARE[1], picked.last())
    }
}
