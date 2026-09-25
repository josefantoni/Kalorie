package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import antoni.kalorie.macrokit.energyKJFromMacros
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MyCreatedMealIngredientDTO(
    @SerialName("food_item_id") val foodItemId: String,
    @SerialName("cz_name") val czName: String,
    @SerialName("eng_name") val engName: String,
    val grams: Double,
    @SerialName("energy_kj") val energyKJ: Double? = null,
    @SerialName("calories_per_hundred_grams") val caloriesPerHundredGrams: Double,
    val fat: Double,
    @SerialName("fat_saturated") val fatSaturated: Double? = null,
    @SerialName("fat_unsaturated_fatty_acids") val fatUnsaturatedFattyAcids: Double,
    val carbohydrate: Double,
    @SerialName("carbohydrate_pure_sugar") val carbohydratePureSugar: Double,
    val fiber: Double? = null,
    val protein: Double,
    val salt: Double,
) {

    // MARK: - Init

    constructor(ingredient: MyCreatedMealIngredientDomain) : this(
        foodItemId = ingredient.foodItemId,
        czName = ingredient.czName,
        engName = ingredient.engName,
        grams = ingredient.grams,
        energyKJ = ingredient.nutrition.energyKJ,
        caloriesPerHundredGrams = ingredient.nutrition.caloriesPerHundredGrams,
        fat = ingredient.nutrition.fat,
        fatSaturated = ingredient.nutrition.fatSaturated,
        fatUnsaturatedFattyAcids = ingredient.nutrition.fatUnsaturatedFattyAcids,
        carbohydrate = ingredient.nutrition.carbohydrate,
        carbohydratePureSugar = ingredient.nutrition.carbohydratePureSugar,
        fiber = ingredient.nutrition.fiber,
        protein = ingredient.nutrition.protein,
        salt = ingredient.nutrition.salt,
    )

    // MARK: - Functions

    fun asDomain(): MyCreatedMealIngredientDomain = MyCreatedMealIngredientDomain(
        foodItemId = foodItemId,
        czName = czName,
        engName = engName,
        grams = grams,
        nutrition = FoodNutritionValues(
            energyKJ = energyKJ ?: energyKJFromMacros(fat = fat, carbohydrate = carbohydrate, protein = protein),
            caloriesPerHundredGrams = caloriesPerHundredGrams,
            fat = fat,
            fatSaturated = fatSaturated,
            fatUnsaturatedFattyAcids = fatUnsaturatedFattyAcids,
            carbohydrate = carbohydrate,
            carbohydratePureSugar = carbohydratePureSugar,
            fiber = fiber,
            protein = protein,
            salt = salt,
        ),
    )
}

@Serializable
data class MyCreatedMealDTO(
    val id: String,
    val name: String,
    val ingredients: List<MyCreatedMealIngredientDTO>,
    @SerialName("created_at") val createdAt: Double,
    @SerialName("updated_at") val updatedAt: Double,
    val portions: List<FoodPortionDTO>? = null,
) {

    // MARK: - Init

    constructor(meal: MyCreatedMealDomain) : this(
        id = meal.id,
        name = meal.name,
        ingredients = meal.ingredients.map(::MyCreatedMealIngredientDTO),
        createdAt = meal.createdAt.epochSecondsAsDouble(),
        updatedAt = meal.updatedAt.epochSecondsAsDouble(),
        portions = meal.portions.map(::FoodPortionDTO),
    )

    // MARK: - Functions

    fun asDomain(): MyCreatedMealDomain = MyCreatedMealDomain(
        id = id,
        name = name,
        ingredients = ingredients.map { it.asDomain() },
        createdAt = instantFromEpochSeconds(createdAt),
        updatedAt = instantFromEpochSeconds(updatedAt),
        portions = portions?.map(FoodPortionDTO::asDomain) ?: emptyList(),
    )
}
