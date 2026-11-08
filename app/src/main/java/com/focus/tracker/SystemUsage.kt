package com.focus.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.Calendar

/**
 * App time straight from Android's own usage log (what Digital Wellbeing shows),
 * so Focus counts days from before it was installed and never misses time while
 * it was not running. Needs usage access; without it every day reads empty.
 */
object SystemUsage {
    /** How far before a day to start reading, for an app already open at midnight. */
    private const val LOOK_BACK_MS = 3 * 60 * 60 * 1000L

    /** Per app, for each of the last [days] days: index 0 is the oldest, the last is today. */
    fun days(c: Context, days: Int = 7, now: Long = System.currentTimeMillis()): List<Map<String, AppUse>> {
        val starts = (days - 1 downTo 0).map { dayStart(it, now) }
        val events = events(c, starts.first() - LOOK_BACK_MS, now)
        return starts.mapIndexed { i, start ->
            val end = if (i + 1 < starts.size) starts[i + 1] else now
            Sessions.tally(events, start, end)
        }
    }

    /** Seconds in [pkg] today so far. */
    fun today(c: Context, pkg: String, now: Long = System.currentTimeMillis()): Int {
        val start = dayStart(0, now)
        return Sessions.tally(events(c, start - LOOK_BACK_MS, now), start, now)[pkg]?.seconds ?: 0
    }

    private fun events(c: Context, from: Long, to: Long): List<UsageEvent> {
        if (!granted(c)) return emptyList()
        val usage = c.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val log = runCatching { usage.queryEvents(from, to) }.getOrNull() ?: return emptyList()
        val out = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (log.hasNextEvent()) {
            log.getNextEvent(e)
            out += UsageEvent(e.timeStamp, e.eventType, e.packageName ?: continue, e.className)
        }
        return out
    }

    private fun dayStart(daysAgo: Int, now: Long): Long = Calendar.getInstance().run {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** Whether the user gave Focus usage access. */
    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            c.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
