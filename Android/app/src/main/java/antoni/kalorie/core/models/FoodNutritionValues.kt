package antoni.kalorie.core.models

data class FoodNutritionValues(
    val energyKJ: Double,
    val caloriesPerHundredGrams: Double,
    val fat: Double,
    val fatSaturated: Double?,
    val fatUnsaturatedFattyAcids: Double,
    val carbohydrate: Double,
    val carbohydratePureSugar: Double,
    val fiber: Double?,
    val protein: Double,
    val salt: Double,
)
