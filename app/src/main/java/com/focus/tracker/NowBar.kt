package com.focus.tracker

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/**
 * The app in front as an Android Live Update, which Samsung's One UI 9 shows in
 * the Now Bar and as a chip in the status bar.
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

    fun show(pkg: String) {
        if (Build.VERSION.SDK_INT < 37) return
        val next = LiveCard.next(
            shown, pkg, Store.seconds(ctx, pkg), SystemClock.elapsedRealtime(), count(pkg)
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
        val label = runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(card.pkg, 0)).toString()
        }.getOrDefault(card.pkg)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val style = Notification.MetricStyle().addMetric(
            Notification.Metric(
                Notification.Metric.TimeDifference.forStopwatch(
                    card.zeroMs, Notification.Metric.TimeDifference.FORMAT_CHRONOMETER
                ),
                "Сьогодні"
            )
        )
        card.count?.let {
            style.addMetric(Notification.Metric(Notification.Metric.FixedInt(it), card.countLabel ?: ""))
        }
        return Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(label)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setStyle(style.setCriticalMetric(0))
            .build()
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
