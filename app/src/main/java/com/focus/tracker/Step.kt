package com.focus.tracker

import android.content.Intent

/**
 * One thing the user still has to switch on, shown as the setup card on the main
 * screen. [intents] are tried in order until one opens (some phones reject the
 * more specific settings screens). [appInfo] adds an "App info" button, where
 * Android hides "Allow restricted settings".
 */
class Step(
    val title: String,
    val body: String,
    val intents: List<Intent>,
    val appInfo: Boolean = false,
)
