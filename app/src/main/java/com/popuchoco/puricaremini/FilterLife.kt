package com.popuchoco.puricaremini

const val DEFAULT_FILTER_TOTAL_HOURS = 2_000
val FILTER_REMINDER_THRESHOLDS = listOf(3, 5, 10, 20)

data class FilterLife(
    val remainingHours: Int,
    val totalHours: Int,
    val usedHours: Int,
    val percent: Int,
    val totalFromDevice: Boolean,
)

fun AirSnapshot.filterLife(): FilterLife? {
    val remaining = filterRemaining?.coerceAtLeast(0) ?: return null
    val reportedTotal = filterTotal?.takeIf { it > 0 }
    val total = reportedTotal ?: DEFAULT_FILTER_TOTAL_HOURS
    val boundedRemaining = remaining.coerceAtMost(total)
    val calculated = boundedRemaining * 100 / total
    val percent = when {
        boundedRemaining == 0 -> 0
        calculated == 0 -> 1
        else -> calculated.coerceAtMost(100)
    }
    return FilterLife(
        remainingHours = boundedRemaining,
        totalHours = total,
        usedHours = (total - boundedRemaining).coerceAtLeast(0),
        percent = percent,
        totalFromDevice = reportedTotal != null,
    )
}

object FilterReminderPolicy {
    fun shouldNotify(percent: Int, threshold: Int, notifiedThreshold: Int?): Boolean =
        threshold in FILTER_REMINDER_THRESHOLDS && percent <= threshold && notifiedThreshold != threshold

    fun shouldReset(percent: Int, threshold: Int, notifiedThreshold: Int?): Boolean =
        notifiedThreshold != null && percent > threshold
}
