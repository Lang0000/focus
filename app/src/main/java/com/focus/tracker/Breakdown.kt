package com.focus.tracker

/**
 * The dashboard's numbers, from the system's per-app use (already narrowed to
 * apps a person opens): the day's list and the week chart's coloured bars.
 */
object Breakdown {
    /** Shorter than this, an app is noise in the list (a share sheet, a quick look). */
    const val MIN_SECONDS = 60

    /** The day's apps with at least [MIN_SECONDS], longest first. */
    fun rows(day: Map<String, AppUse>): List<Pair<String, AppUse>> =
        day.filter { it.value.seconds >= MIN_SECONDS }.toList().sortedByDescending { it.second.ms }

    /** The [n] apps with the most time over [week]: each gets its own colour on the chart. */
    fun top(week: List<Map<String, AppUse>>, n: Int): List<String> {
        val total = HashMap<String, Long>()
        for (day in week) for ((pkg, use) in day) total[pkg] = (total[pkg] ?: 0L) + use.ms
        return total.entries.sortedByDescending { it.value }.take(n).map { it.key }
    }

    /** One day's bar: seconds per [top] app in that order (empty ones dropped), then the rest as null. */
    fun bar(day: Map<String, AppUse>, top: List<String>): List<Pair<String?, Int>> {
        val parts = top.mapNotNull { pkg -> day[pkg]?.seconds?.takeIf { it > 0 }?.let { pkg to it } }
        val rest = day.filterKeys { it !in top }.values.sumOf { it.seconds }
        return if (rest > 0) parts + (null to rest) else parts
    }
}
