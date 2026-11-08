package com.focus.tracker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils

/** Full build: everything runs in the accessibility service. */
object Setup {
    const val REELS = true

    fun next(c: Context): Step? {
        if (!serviceEnabled(c)) return Step(
            title = "Увімкни Focus",
            body = "Щоб бачити, який додаток відкритий, і показувати віконце, увімкни службу «Focus» у Спеціальних можливостях.\n\n" +
                "Якщо перемикач сірий: Налаштування → Додатки → Focus → ⋮ → «Дозволити обмежені налаштування», і спробуй ще раз.",
            intents = listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            appInfo = true,
        )
        if (!SystemUsage.granted(c)) return Step(
            title = "Дозволь доступ до використання",
            body = "Час у додатках Focus бере із системної статистики, тож видно й дні до встановлення. " +
                "Знайди Focus у списку «Доступ до даних про використання» й увімкни.",
            intents = listOf(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${c.packageName}")),
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            ),
        )
        return null
    }

    /** The system starts the accessibility service itself. */
    fun start(c: Context) {}

    private fun serviceEnabled(c: Context): Boolean {
        val enabled = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(c, FocusService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (s in splitter) {
            if (ComponentName.unflattenFromString(s) == me) return true
        }
        return false
    }
}
