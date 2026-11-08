package com.focus.tracker

/**
 * Follows which app is in front from the system's usage events: an activity
 * resuming puts its app in front; the same activity pausing (screen off, home,
 * recents) leaves nothing in front until the next one resumes.
 *
 * Replaying events already seen is harmless, so the service can re-read a
 * few seconds of overlap on every poll and never miss a late-logged event.
 */
class Front {
    var pkg: String? = null
        private set
    private var cls: String? = null

    fun on(type: Int, pkg: String, cls: String?) {
        when (type) {
            RESUMED -> { this.pkg = pkg; this.cls = cls }
            PAUSED -> if (pkg == this.pkg && cls == this.cls) { this.pkg = null; this.cls = null }
        }
    }

    companion object {
        /** UsageEvents.Event.ACTIVITY_RESUMED (formerly MOVE_TO_FOREGROUND). */
        const val RESUMED = 1
        /** UsageEvents.Event.ACTIVITY_PAUSED (formerly MOVE_TO_BACKGROUND). */
        const val PAUSED = 2
    }
}
