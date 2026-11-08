package com.focus.tracker

/**
 * The colour an app's icon is "about", for the live card: the most common
 * strong hue among its opaque pixels, averaged. Null for an icon that is
 * black, white and grey (Threads, X) - those get a neutral card.
 */
object Accent {
    private const val BINS = 24
    /** Below this share of opaque pixels, a hue is decoration, not the icon's colour. */
    private const val MIN_SHARE = 0.08

    fun of(argb: IntArray): Int? {
        val count = IntArray(BINS)
        val r = LongArray(BINS); val g = LongArray(BINS); val b = LongArray(BINS)
        var opaque = 0
        for (p in argb) {
            if ((p ushr 24) < 200) continue
            opaque++
            val bin = hueBin(p) ?: continue
            count[bin]++
            r[bin] += (p shr 16 and 0xFF).toLong()
            g[bin] += (p shr 8 and 0xFF).toLong()
            b[bin] += (p and 0xFF).toLong()
        }
        val top = count.indices.maxByOrNull { count[it] } ?: return null
        val n = count[top]
        if (opaque == 0 || n < opaque * MIN_SHARE) return null
        return (0xFF shl 24) or ((r[top] / n).toInt() shl 16) or ((g[top] / n).toInt() shl 8) or (b[top] / n).toInt()
    }

    /** The hue bin of a strong colour, or null for grey, near-black or washed-out pixels. */
    private fun hueBin(p: Int): Int? {
        val r = p shr 16 and 0xFF; val g = p shr 8 and 0xFF; val b = p and 0xFF
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val delta = max - min
        if (max < 64 || delta < max * 0.35) return null
        val hue = when (max) {
            r -> 60.0 * (((g - b).toDouble() / delta).mod(6.0))
            g -> 60.0 * ((b - r).toDouble() / delta + 2)
            else -> 60.0 * ((r - g).toDouble() / delta + 4)
        }
        return ((hue / 360.0 * BINS).toInt()).coerceIn(0, BINS - 1)
    }
}
