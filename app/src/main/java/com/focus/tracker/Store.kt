package com.focus.tracker

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * All state lives in one SharedPreferences file, shared by the activity and the
 * tracker service (same process). Per-day stats are keyed by date, so a
 * new day starts at zero on its own with no reset job.
 *
 *   t|<date>|<pkg>   seconds in that app
 *   reels|<date>     Instagram Reels counted
 *   shorts|<date>    YouTube Shorts counted
 *   reelsec|<date>   seconds on the Reels player
 *   shortsec|<date>  seconds on the Shorts player
 */
object Store {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    private val DEFAULT_APPS = setOf(
        INSTAGRAM, YOUTUBE,
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", // TikTok
        "com.facebook.katana", "com.twitter.android", "org.telegram.messenger"
    )

    private const val APPS = "apps"
    private const val OVERLAY = "overlay"
    private const val COUNT_REELS = "countReels"
    private const val PILL_X = "pillX"
    private const val PILL_Y = "pillY"

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun day(offset: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // --- Settings -------------------------------------------------------------

    fun apps(c: Context): Set<String> =
        prefs(c).getStringSet(APPS, null) ?: DEFAULT_APPS

    fun setApps(c: Context, apps: Set<String>) =
        prefs(c).edit().putStringSet(APPS, HashSet(apps)).apply()

    fun overlayOn(c: Context) = prefs(c).getBoolean(OVERLAY, true)

    fun nowBarOn(c: Context) = prefs(c).getBoolean("nowBar", true)
    fun setNowBarOn(c: Context, on: Boolean) = prefs(c).edit().putBoolean("nowBar", on).apply()
    fun setOverlayOn(c: Context, on: Boolean) = prefs(c).edit().putBoolean(OVERLAY, on).apply()

    fun countReels(c: Context) = prefs(c).getBoolean(COUNT_REELS, true)
    fun setCountReels(c: Context, on: Boolean) = prefs(c).edit().putBoolean(COUNT_REELS, on).apply()

    fun pillPos(c: Context): Pair<Int, Int>? {
        val p = prefs(c)
        if (!p.contains(PILL_Y)) return null
        return p.getInt(PILL_X, 0) to p.getInt(PILL_Y, 0)
    }

    fun setPillPos(c: Context, x: Int, y: Int) =
        prefs(c).edit().putInt(PILL_X, x).putInt(PILL_Y, y).apply()

    /** Bumped when the user resets the pill, so the service rebuilds it in the default spot. */
    fun pillResets(c: Context) = prefs(c).getInt("pillResets", 0)

    fun resetPillPos(c: Context) {
        val p = prefs(c)
        p.edit().remove(PILL_X).remove(PILL_Y).putInt("pillResets", pillResets(c) + 1).apply()
    }

    // --- Stats ----------------------------------------------------------------

    private fun inc(c: Context, key: String, by: Int = 1) {
        val p = prefs(c)
        p.edit().putInt(key, p.getInt(key, 0) + by).apply()
    }

    fun addSeconds(c: Context, pkg: String, s: Int = 1) = inc(c, "t|${today()}|$pkg", s)
    fun seconds(c: Context, pkg: String, date: String = today()) =
        prefs(c).getInt("t|$date|$pkg", 0)

    /** Seconds per app on [date], biggest first. */
    fun dayApps(c: Context, date: String = today()): List<Pair<String, Int>> {
        val prefix = "t|$date|"
        return prefs(c).all.mapNotNull { (k, v) ->
            if (k.startsWith(prefix) && v is Int && v > 0) k.removePrefix(prefix) to v else null
        }.sortedByDescending { it.second }
    }

    fun dayTotal(c: Context, date: String = today()) = dayApps(c, date).sumOf { it.second }

    fun addReel(c: Context) = inc(c, "reels|${today()}")
    fun addShort(c: Context) = inc(c, "shorts|${today()}")
    fun addReelSec(c: Context, s: Int = 1) = inc(c, "reelsec|${today()}", s)
    fun addShortSec(c: Context, s: Int = 1) = inc(c, "shortsec|${today()}", s)

    fun reels(c: Context, date: String = today()) = prefs(c).getInt("reels|$date", 0)
    fun shorts(c: Context, date: String = today()) = prefs(c).getInt("shorts|$date", 0)
    fun reelSec(c: Context, date: String = today()) = prefs(c).getInt("reelsec|$date", 0)
    fun shortSec(c: Context, date: String = today()) = prefs(c).getInt("shortsec|$date", 0)
}
