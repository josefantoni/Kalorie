package antoni.kalorie.mealkit

private const val MINUTES_PER_DAY = 1440

const val MIN_MEAL_WINDOW_MINUTES = 30

data class DefaultMealWindow(val key: String, val startMinutes: Int, val endMinutes: Int)

val DEFAULT_MEAL_WINDOWS = listOf(
    DefaultMealWindow(key = "breakfast", startMinutes = 5 * 60, endMinutes = 8 * 60 + 30),
    DefaultMealWindow(key = "secondBreakfast", startMinutes = 8 * 60 + 30, endMinutes = 11 * 60),
    DefaultMealWindow(key = "lunch", startMinutes = 11 * 60, endMinutes = 14 * 60 + 30),
    DefaultMealWindow(key = "snack", startMinutes = 14 * 60 + 30, endMinutes = 17 * 60),
    DefaultMealWindow(key = "dinner", startMinutes = 17 * 60, endMinutes = 20 * 60),
)

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

fun isMealWindowLongEnough(
    startMinutes: Int,
    endMinutes: Int,
    minimumDurationMinutes: Int = MIN_MEAL_WINDOW_MINUTES
): Boolean =
    dayRanges(startMinutes, endMinutes).sumOf { (start, end) -> end - start } >= minimumDurationMinutes

data class MealWindow(val id: String, val startMinutes: Int, val endMinutes: Int)

fun mealWindowAt(minutes: Int, windows: List<MealWindow>): MealWindow? =
    windows.sortedBy { it.startMinutes }.firstOrNull {
        isMinuteWithinWindow(minutes, it.startMinutes, it.endMinutes)
    }

fun copyTargetMinutes(nowMinutes: Int?, targetId: String, windows: List<MealWindow>): Int? {
    val target = windows.firstOrNull { it.id == targetId } ?: return null
    return if (nowMinutes != null && isMinuteWithinWindow(nowMinutes, target.startMinutes, target.endMinutes)) {
        nowMinutes
    } else {
        target.startMinutes
    }
}

fun resolvedMealWindowId(minutes: Int, pinnedId: String?, windows: List<MealWindow>): String? =
    if (pinnedId != null && windows.any { it.id == pinnedId }) {
        pinnedId
    } else {
        mealWindowAt(minutes, windows)?.id
    }
