package com.focus.tracker

/** One entry of the system's usage event log (UsageEvents.Event), as plain data. */
data class UsageEvent(val time: Long, val type: Int, val pkg: String, val cls: String?)

/** One app over a span of time: how long it was in front and how often it was opened. */
data class AppUse(val ms: Long, val opens: Int) {
    val seconds: Int get() = (ms / 1000).toInt()
}

/**
 * Reads the system's usage event log the way Digital Wellbeing does: an app is
 * in front from the first of its activities resuming until the last one pauses,
 * another app comes to front, or the screen goes off or locks. Each such visit
 * that starts inside the span is one "open".
 */
object Sessions {
    const val RESUMED = 1          // UsageEvents.Event.ACTIVITY_RESUMED
    const val PAUSED = 2           // ACTIVITY_PAUSED
    const val SCREEN_OFF = 16      // SCREEN_NON_INTERACTIVE
    const val KEYGUARD = 17        // KEYGUARD_SHOWN
    const val STOPPED = 23         // ACTIVITY_STOPPED
    const val SHUTDOWN = 26        // DEVICE_SHUTDOWN

    /** Time and opens per app in [from, to) (ms, wall clock), from [events] in log order. */
    fun tally(events: List<UsageEvent>, from: Long, to: Long): Map<String, AppUse> {
        val visits = HashMap<String, Visit>()
        val ms = HashMap<String, Long>()
        val opens = HashMap<String, Int>()

        fun end(pkg: String, at: Long) {
            val v = visits.remove(pkg) ?: return
            val span = at - maxOf(v.start, from)
            if (span > 0) ms[pkg] = (ms[pkg] ?: 0L) + span
        }
        fun endAll(at: Long) = visits.keys.toList().forEach { end(it, at) }

        for (e in events) {
            if (e.time >= to) break
            when (e.type) {
                RESUMED -> {
                    visits.keys.filter { it != e.pkg }.forEach { end(it, e.time) }
                    val v = visits.getOrPut(e.pkg) {
                        if (e.time >= from) opens[e.pkg] = (opens[e.pkg] ?: 0) + 1
                        Visit(e.time)
                    }
                    v.screens += e.cls
                }
                PAUSED, STOPPED -> visits[e.pkg]?.let { v ->
                    v.screens -= e.cls
                    if (v.screens.isEmpty()) end(e.pkg, e.time)
                }
                SCREEN_OFF, KEYGUARD, SHUTDOWN -> endAll(e.time)
            }
        }
        endAll(to)
        return (ms.keys + opens.keys).associateWith { AppUse(ms[it] ?: 0L, opens[it] ?: 0) }
    }

    private class Visit(val start: Long) {
        val screens = HashSet<String?>()
    }
}
