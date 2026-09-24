package antoni.kalorie.core.models

import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.mealkit.isMinuteWithinWindow
import java.time.Instant

data class MealTypeDomain(
    val id: String,
    val name: String,
    val startTime: Instant,
    val endTime: Instant,
)

fun List<MealTypeDomain>.mealType(date: Instant): MealTypeDomain? {
    val minutes = date.minutesSinceMidnight()
    return sortedBy { it.startTime }.firstOrNull {
        isMinuteWithinWindow(
            minutes = minutes,
            startMinutes = it.startTime.minutesSinceMidnight(),
            endMinutes = it.endTime.minutesSinceMidnight(),
        )
    }
}

fun List<MealTypeDomain>.resolvedMealTypeId(food: FoodConsumedDomain): String? {
    val pinnedMealTypeId = food.mealTypeId
    if (pinnedMealTypeId != null && any { it.id == pinnedMealTypeId }) {
        return pinnedMealTypeId
    }
    return mealType(food.date)?.id
}
