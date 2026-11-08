package com.focus.tracker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Lite build: no accessibility service, so Play Protect lets the APK install.
 * The foreground app comes from usage access; the pill needs "display over
 * other apps". Neither is a restricted setting, so no extra unlock is needed.
 */
object Setup {
    const val REELS = false

    fun next(c: Context): Step? {
        val pkg = Uri.parse("package:${c.packageName}")
        if (!usageAccess(c)) return Step(
            title = "Дозволь доступ до використання",
            body = "Focus дізнається, який додаток відкритий, із системної статистики використання. " +
                "Знайди Focus у списку «Доступ до даних про використання» й увімкни.",
            intents = listOf(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg),
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            ),
        )
        if (Store.overlayOn(c) && !Settings.canDrawOverlays(c)) return Step(
            title = "Дозволь віконце",
            body = "Щоб показувати таймер поверх додатків, увімкни для Focus «Показ поверх інших додатків». " +
                "Час рахується й без цього.",
            intents = listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            ),
        )
        return null
    }

    /** Start the tracker if it may run; safe to call repeatedly. */
    fun start(c: Context) {
        if (!usageAccess(c)) return
        runCatching { c.startForegroundService(Intent(c, UsageService::class.java)) }
    }

    fun usageAccess(c: Context): Boolean {
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
