package com.focus.tracker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build

/**
 * A tracked app's icons for the live card: the full-colour launcher icon, and
 * the app's one-colour system icon - the monochrome layer of its adaptive icon,
 * the one Android's themed icons use (Android 13+).
 *
 * One UI draws the capsule and the expanded card from the same small icon. A
 * one-colour icon becomes a white glyph on the capsule and a white glyph on a
 * tile in the card's colour when expanded. Samsung's own tray icon
 * (semGetApplicationIconForIconTray) was tried and is full colour, so it is not
 * used.
 */
class AppIcons(private val ctx: Context) {
    private val full = HashMap<String, Icon?>()
    private val tray = HashMap<String, Icon?>()
    private val accent = HashMap<String, Int?>()

    /** The launcher icon in colour. */
    fun full(pkg: String): Icon? = full.getOrPut(pkg) {
        runCatching { bitmapIcon(ctx.packageManager.getApplicationIcon(pkg), 48) }.getOrNull()
    }

    /** The icon's own colour (see [Accent]), or null for a black-and-white icon. */
    fun accent(pkg: String): Int? = accent.getOrPut(pkg) {
        runCatching {
            val bmp = bitmap(ctx.packageManager.getApplicationIcon(pkg), 48)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            Accent.of(px)
        }.getOrNull()
    }

    /** The one-colour status bar icon, or null when the app has none. */
    fun tray(pkg: String): Icon? = tray.getOrPut(pkg) {
        runCatching {
            val info = ctx.packageManager.getApplicationInfo(pkg, 0)
            val d = monochrome(info) ?: return@runCatching null
            bitmapIcon(d, 24, zoom = GLYPH_ZOOM)
        }.getOrNull()
    }


    private fun monochrome(info: ApplicationInfo): Drawable? {
        if (Build.VERSION.SDK_INT < 33) return null
        val icon = ctx.packageManager.getApplicationIcon(info) as? AdaptiveIconDrawable ?: return null
        return icon.monochrome
    }

    /** [d] drawn [dp] wide; [zoom] > 1 crops the empty margin around a centred glyph. */
    private fun bitmapIcon(d: Drawable, dp: Int, zoom: Float = 1f): Icon =
        Icon.createWithBitmap(bitmap(d, dp, zoom))

    private fun bitmap(d: Drawable, dp: Int, zoom: Float = 1f): Bitmap {
        val size = ctx.dp(dp)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pad = ((zoom - 1f) * size / 2f).toInt()
        d.setBounds(-pad, -pad, size + pad, size + pad)
        d.draw(Canvas(bmp))
        return bmp
    }

    private companion object {
        /**
         * An adaptive icon's monochrome layer is 108 dp with the glyph in about
         * the middle 48-60 dp; drawn as is it looks tiny in the status bar.
         */
        const val GLYPH_ZOOM = 1.8f
    }
}
