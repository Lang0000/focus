package com.focus.tracker

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        if (!SystemUsage.granted(c)) return Step(
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
        if (!SystemUsage.granted(c)) return
        runCatching { c.startForegroundService(Intent(c, UsageService::class.java)) }
    }

}
