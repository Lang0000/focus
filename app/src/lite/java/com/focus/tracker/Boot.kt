package com.focus.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the tracker after a reboot or an app update. */
class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Setup.start(c)
        }
    }
}
