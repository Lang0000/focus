package com.focus.tracker

/**
 * The week chart's colours: each top app in its icon's own colour, unless it
 * has none (a black-and-white icon) or it would look like an earlier app's -
 * then the next spare colour nobody uses yet.
 */
object Palette {
    val SPARE = intArrayOf(
        0xFF8FA8FF.toInt(), 0xFF7ED6A5.toInt(), 0xFFF5B85B.toInt(),
        0xFFC792EA.toInt(), 0xFF5EC8E5.toInt(), 0xFFFF8A65.toInt(),
    )
    /** Colours closer than this (RGB distance) read as the same on a thin bar. */
    private const val MIN_DISTANCE = 90

    /** The rest of the day: apps outside the top ones. */
    const val REST = 0xFF3A3F48.toInt()

    fun pick(accents: List<Int?>): List<Int> {
        val used = ArrayList<Int>()
        for (accent in accents) {
            val c = accent?.takeIf { a -> used.none { close(it, a) } }
                ?: SPARE.firstOrNull { s -> used.none { close(it, s) } }
                ?: SPARE[used.size % SPARE.size]
            used += c
        }
        return used
    }

    private fun close(a: Int, b: Int): Boolean {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db < MIN_DISTANCE * MIN_DISTANCE
    }
}
