package antoni.kalorie.core.models

import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.mealkit.MealWindow
import antoni.kalorie.mealkit.mealWindowAt
import antoni.kalorie.mealkit.resolvedMealWindowId
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class MealTypeDomain(
    val id: String,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
) {

    // MARK: - Functions

    companion object {
        fun clockTime(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    }
}

fun List<MealTypeDomain>.mealType(date: Instant): MealTypeDomain? {
    val id = mealWindowAt(date.minutesSinceMidnight(), mealWindows())?.id ?: return null
    return firstOrNull { it.id == id }
}

fun List<MealTypeDomain>.resolvedMealTypeId(food: FoodConsumedDomain, zone: ZoneId = ZoneId.systemDefault()): String? =
    resolvedMealWindowId(food.date.minutesSinceMidnight(zone), food.mealTypeId, mealWindows())

private fun List<MealTypeDomain>.mealWindows(): List<MealWindow> =
    map {
        MealWindow(
            id = it.id,
            startMinutes = it.startMinutes,
            endMinutes = it.endMinutes,
        )
    }
