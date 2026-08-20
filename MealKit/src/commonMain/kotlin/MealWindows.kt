private const val MINUTES_PER_DAY = 1440

fun minutesSinceMidnight(hour: Int, minute: Int): Int = hour * 60 + minute

/**
 * Splits a window into one or two same-day [start, end) ranges, so callers never have to reason
 * about wraparound themselves. A window wraps midnight whenever endMinutes <= startMinutes.
 */
private fun dayRanges(startMinutes: Int, endMinutes: Int): List<Pair<Int, Int>> =
    if (endMinutes > startMinutes) {
        listOf(startMinutes to endMinutes)
    } else {
        listOf(startMinutes to MINUTES_PER_DAY, 0 to endMinutes)
    }

fun isMinuteWithinWindow(minutes: Int, startMinutes: Int, endMinutes: Int): Boolean =
    dayRanges(startMinutes, endMinutes).any { (start, end) -> minutes >= start && minutes < end }

fun mealWindowsOverlap(startMinutes: Int, endMinutes: Int, otherStartMinutes: Int, otherEndMinutes: Int): Boolean {
    val ranges = dayRanges(startMinutes, endMinutes)
    val otherRanges = dayRanges(otherStartMinutes, otherEndMinutes)
    return ranges.any { (start, end) -> otherRanges.any { (otherStart, otherEnd) -> start < otherEnd && end > otherStart } }
}

fun isMealWindowLongEnough(startMinutes: Int, endMinutes: Int, minimumDurationMinutes: Int): Boolean =
    dayRanges(startMinutes, endMinutes).sumOf { (start, end) -> end - start } >= minimumDurationMinutes
