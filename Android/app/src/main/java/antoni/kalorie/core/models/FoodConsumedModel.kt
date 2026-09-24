package antoni.kalorie.core.models

import antoni.kalorie.macrokit.Macros
import antoni.kalorie.macrokit.scaled
import antoni.kalorie.macrokit.scaledCalories
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

data class ScaledMacros private constructor(
    val calories: Int,
    val energyKJ: Double,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatSaturated: Double?,
    val fatUnsaturated: Double,
    val fiber: Double?,
    val salt: Double,
) {

    // MARK: - Init

    private constructor(calories: Int, scaled: Macros, energyKJ: Double, fatSaturated: Double?, fiber: Double?) : this(
        calories = calories,
        energyKJ = energyKJ,
        protein = scaled.protein,
        carbohydrate = scaled.carbohydrate,
        carbohydrateSugar = scaled.carbohydrateSugar,
        fat = scaled.fat,
        fatSaturated = fatSaturated,
        fatUnsaturated = scaled.fatUnsaturated,
        fiber = fiber,
        salt = scaled.salt,
    )

    // calories is scaled separately below: caloriesPerHundredGrams is fractional, and rounding
    // it here before .scaled() would round twice instead of once.
    constructor(item: FoodItemDomain, ratio: Double) : this(
        calories = scaledCalories(caloriesPerHundredGrams = item.caloriesPerHundredGrams, ratio = ratio),
        scaled = Macros(
            calories = 0,
            protein = item.protein,
            carbohydrate = item.carbohydrate,
            carbohydrateSugar = item.carbohydratePureSugar,
            fat = item.fat,
            fatUnsaturated = item.fatUnsaturatedFattyAcids,
            fiber = item.fiber ?: 0.0,
            salt = item.salt,
        ).scaled(factor = ratio),
        energyKJ = item.energyKJ * ratio,
        fatSaturated = item.fatSaturated?.let { it * ratio },
        fiber = item.fiber?.let { it * ratio },
    )
}

fun FoodItemDomain.scaled(toGrams: Double): ScaledMacros = ScaledMacros(item = this, ratio = toGrams / 100)
