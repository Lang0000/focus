package com.focus.tracker

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

/**
 * Lite build tracker: a foreground service that, once a second, reads the new
 * usage events to learn which app is in front and, when it is one the user
 * picked, shows the pill as an app overlay. The time itself is the system's
 * (SystemUsage), so nothing is counted here.
 *
 * Uses usage access + "display over other apps" instead of an accessibility
 * service, so the APK is not blocked by Play Protect when sideloaded.
 */
class UsageService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usage: UsageStatsManager
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private lateinit var screen: Screen

    private val front = Front()
    private var readTo = 0L

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { tick() }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        screen = Screen(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, canDraw = { Settings.canDrawOverlays(this) })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!SystemUsage.granted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        screen.remove()
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Відлік часу", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Focus рахує час")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    /** Feed usage events since the last read (plus a little overlap) into [front]. */
    private fun readEvents() {
        val now = System.currentTimeMillis()
        val from = if (readTo == 0L) now - FIRST_LOOK_MS else readTo - OVERLAP_MS
        val events = usage.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            front.on(e.eventType, pkg, e.className)
        }
        readTo = now
    }

    private fun tick() {
        readEvents()
        val fg = front.pkg?.takeIf {
            power.isInteractive && !keyguard.isKeyguardLocked && it in Store.apps(this)
        }
        if (fg == null) {
            screen.hide()
            return
        }
        screen.show(fg)
    }

    companion object {
        private const val CHANNEL = "tracking"
        private const val NOTIFICATION_ID = 1
        /** On start, how far back to look for the app already in front. */
        private const val FIRST_LOOK_MS = 30 * 60 * 1000L
        /** Re-read this much on every poll, for events logged a moment late. */
        private const val OVERLAP_MS = 3000L
    }
}
