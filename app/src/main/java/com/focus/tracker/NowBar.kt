package com.focus.tracker

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings

/**
 * The app in front as an Android Live Update, which Samsung's One UI 9 shows as
 * a capsule in the status bar (tap: it expands into the card) and in the shade.
 *
 * The card uses Android 17's MetricStyle: a stopwatch for today's time in the
 * app (the status bar chip shows it) and the Reels/Shorts count. Older Android
 * gets no card: Live Update promotion arrived in 16 QPR2 and MetricStyle in 17,
 * and one code path is easier to trust than three.
 *
 * The system runs the stopwatch, so the card is posted only when [LiveCard.next]
 * says it changed.
 */
class NowBar(private val ctx: Context, private val count: (String) -> Int?) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var shown: LiveCard? = null
    private var channelMade = false
    private val iconCache = HashMap<String, Icon?>()

    fun show(pkg: String) {
        if (Build.VERSION.SDK_INT < 37) return
        val next = LiveCard.next(
            shown, pkg,
            secondsToday = Store.seconds(ctx, pkg),
            nowMs = SystemClock.elapsedRealtime(),
            count = count(pkg),
        )
        if (next == shown) return
        shown = next
        runCatching { nm.notify(ID, build(next)) }
    }

    /** Recheck the card for the app already shown (e.g. a reel was just counted). */
    fun update() {
        shown?.let { show(it.pkg) }
    }

    fun hide() {
        if (shown == null) return
        shown = null
        nm.cancel(ID)
    }

    @TargetApi(37)
    private fun build(card: LiveCard): Notification {
        makeChannel()
        val pm = ctx.packageManager
        val info = runCatching { pm.getApplicationInfo(card.pkg, 0) }.getOrNull()
        val label = info?.let { pm.getApplicationLabel(it).toString() } ?: card.pkg
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val style = Notification.MetricStyle()
            .addMetric(stopwatch(card.zeroMs, "Сьогодні"))
        card.count?.let {
            style.addMetric(Notification.Metric(Notification.Metric.FixedInt(it), card.countLabel ?: ""))
        }
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(appIcon(card.pkg) ?: Icon.createWithResource(ctx, R.drawable.ic_stat))
            .setContentTitle(label)
            .setColor(card.color)
            .setShowWhen(false)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setStyle(style.setCriticalMetric(0))
        if (!liveForAllApps(ctx)) b.addExtras(Bundle().apply { putBoolean(SAMSUNG_AUTOMATION, true) })
        return b.build()
    }

    @TargetApi(37)
    private fun stopwatch(zeroMs: Long, label: String) = Notification.Metric(
        Notification.Metric.TimeDifference.forStopwatch(zeroMs, Notification.Metric.TimeDifference.FORMAT_CHRONOMETER),
        label,
    )

    /** The tracked app's own icon, so the capsule and the card show YouTube, not Focus. */
    private fun appIcon(pkg: String): Icon? = iconCache.getOrPut(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val size = ctx.dp(48)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
            Icon.createWithBitmap(bmp)
        }.getOrNull()
    }

    private fun makeChannel() {
        if (channelMade) return
        // A Live Update's channel must not be IMPORTANCE_MIN. Kept silent.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Now Bar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        channelMade = true
    }

    companion object {
        private const val CHANNEL = "live"
        private const val ID = 7

        /**
         * One UI keeps its own allowlist for the status bar capsule and ignores a
         * plain promoted notification (on SM-S928B / One UI 9 the card reached the
         * shade, but SystemUI had isOngoingActivity=false and no capsule). This
         * undocumented extra, from decompiled One UI SystemUI, lets a notification
         * that already passes Android's promotion checks through. Do NOT also set
         * android.ongoingActivityNoti.style: that takes Samsung's private-card
         * path and cancels it. Side effect seen: the expanded capsule's header
         * reads "Gemini Intelligence" instead of the app name: in SystemUI this is
         * the path for Gemini's automations. Used only while [liveForAllApps] is off.
         * Source: github.com/tigerduck-app/tigerduck-app-android/pull/126
         */
        private const val SAMSUNG_AUTOMATION = "android.ongoingActivityNoti.automation"

        /**
         * One UI's developer option "Live notifications for all apps" (Developer
         * options), read by SystemUI as Settings.Secure enable_notification_nowbar_test.
         * When it is on, the allowlist is off and the card needs no
         * [SAMSUNG_AUTOMATION] - so it shows under Focus's own name.
         * False when the key is missing or unreadable (non-Samsung phones).
         */
        private const val SAMSUNG_ALL_APPS_LIVE = "enable_notification_nowbar_test"

        fun liveForAllApps(c: Context): Boolean = runCatching {
            Settings.Secure.getInt(c.contentResolver, SAMSUNG_ALL_APPS_LIVE, 0) == 1
        }.getOrDefault(false)
        val SUPPORTED = Build.VERSION.SDK_INT >= 37

        /** What the user still has to allow for the card to reach the Now Bar, if anything. */
        fun step(c: Context): Step? {
            if (Build.VERSION.SDK_INT < 37 || !Store.nowBarOn(c)) return null
            val nm = c.getSystemService(NotificationManager::class.java)
            val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
            val info = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
            if (!nm.areNotificationsEnabled()) return Step(
                title = "Дозволь сповіщення",
                body = "Щоб показувати час у Now Bar і в рядку стану, Focus потрібні сповіщення.",
                intents = listOf(app, info),
            )
            if (!nm.canPostPromotedNotifications()) return Step(
                title = "Дозволь живі сповіщення",
                body = "Увімкни для Focus «Живі сповіщення» (Live notifications), щоб таймер був у Now Bar.",
                intents = listOf(
                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
                    app,
                ),
            )
            return null
        }
    }
}
