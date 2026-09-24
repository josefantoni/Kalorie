package antoni.kalorie.core.models

import java.time.Instant

data class FoodItemDomain(
    val id: String,
    val kind: FoodItemKind,
    override val czName: String,
    override val engName: String,
    val weight: Double,
    val date: Instant,
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
    val portions: List<FoodPortionDomain> = emptyList(),
    val measure: FoodMeasure = FoodMeasure.GRAMS,
) : BilingualNamed {

    // MARK: - Init

    constructor(
        id: String,
        kind: FoodItemKind,
        czName: String,
        engName: String,
        weight: Double,
        date: Instant,
        nutrition: FoodNutritionValues,
    ) : this(
        id = id,
        kind = kind,
        czName = czName,
        engName = engName,
        weight = weight,
        date = date,
        energyKJ = nutrition.energyKJ,
        caloriesPerHundredGrams = nutrition.caloriesPerHundredGrams,
        fat = nutrition.fat,
        fatSaturated = nutrition.fatSaturated,
        fatUnsaturatedFattyAcids = nutrition.fatUnsaturatedFattyAcids,
        carbohydrate = nutrition.carbohydrate,
        carbohydratePureSugar = nutrition.carbohydratePureSugar,
        fiber = nutrition.fiber,
        protein = nutrition.protein,
        salt = nutrition.salt,
    )

    // MARK: - Properties

    val barcode: String?
        get() = if (FoodItemValidation.isValidBarcode(id)) id else null

    val nutrition: FoodNutritionValues
        get() = FoodNutritionValues(
            energyKJ = energyKJ,
            caloriesPerHundredGrams = caloriesPerHundredGrams,
            fat = fat,
            fatSaturated = fatSaturated,
            fatUnsaturatedFattyAcids = fatUnsaturatedFattyAcids,
            carbohydrate = carbohydrate,
            carbohydratePureSugar = carbohydratePureSugar,
            fiber = fiber,
            protein = protein,
            salt = salt,
        )

    // MARK: - Functions

    fun withId(id: String): FoodItemDomain = copy(id = id)
}
