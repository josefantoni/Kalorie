package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.utils.instantFromEpochSeconds
import antoni.kalorie.macrokit.caloriesPerHundredGrams as derivedCaloriesPerHundredGrams
import antoni.kalorie.macrokit.energyKJFromMacros
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodConsumedDTO(
    val id: String,
    @SerialName("food_item_id") val foodItemId: String,
    @SerialName("food_item_kind") val foodItemKind: FoodItemKind,
    @SerialName("cz_name") val czName: String,
    @SerialName("eng_name") val engName: String,
    val weight: Double,
    val date: Double,
    val calories: Int,
    @SerialName("calories_per_hundred_grams") val caloriesPerHundredGrams: Double? = null,
    @SerialName("energy_kj") val energyKJ: Double? = null,
    val protein: Double,
    val carbohydrate: Double,
    @SerialName("carbohydrate_sugar") val carbohydrateSugar: Double,
    val fat: Double,
    @SerialName("fat_saturated") val fatSaturated: Double? = null,
    @SerialName("fat_unsaturated") val fatUnsaturated: Double,
    val fiber: Double? = null,
    val salt: Double,
    @SerialName("meal_type_id") val mealTypeId: String? = null,
    @SerialName("measure_unit") val measureUnit: String? = null,
) {

    // MARK: - Functions

    fun asDomain(): FoodConsumedDomain = FoodConsumedDomain(
        id = id,
        foodItemId = foodItemId,
        foodItemKind = foodItemKind,
        czName = czName,
        engName = engName,
        weight = weight,
        date = instantFromEpochSeconds(date),
        calories = calories,
        caloriesPerHundredGrams = caloriesPerHundredGrams ?: derivedCaloriesPerHundredGrams(calories = calories, weight = weight),
        energyKJ = energyKJ ?: energyKJFromMacros(fat = fat, carbohydrate = carbohydrate, protein = protein),
        protein = protein,
        carbohydrate = carbohydrate,
        carbohydrateSugar = carbohydrateSugar,
        fat = fat,
        fatSaturated = fatSaturated,
        fatUnsaturated = fatUnsaturated,
        fiber = fiber,
        salt = salt,
        mealTypeId = mealTypeId,
        measure = measureUnit?.let(FoodMeasure::fromRawValue) ?: FoodMeasure.GRAMS,
    )
}
