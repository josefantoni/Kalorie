package antoni.kalorie.core.models

import java.time.Instant

data class FoodConsumedDomain(
    val id: String,
    val foodItemId: String,
    val foodItemKind: FoodItemKind,
    override val czName: String,
    override val engName: String,
    val weight: Double,
    val date: Instant,
    val calories: Int,
    val caloriesPerHundredGrams: Double,
    val energyKJ: Double,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatSaturated: Double?,
    val fatUnsaturated: Double,
    val fiber: Double?,
    val salt: Double,
    val mealTypeId: String?,
    val measure: FoodMeasure = FoodMeasure.GRAMS,
) : BilingualNamed
