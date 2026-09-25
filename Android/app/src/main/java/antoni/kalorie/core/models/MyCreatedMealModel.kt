package antoni.kalorie.core.models

import antoni.kalorie.macrokit.weightedMeanPerHundredGrams
import java.time.Instant

data class MyCreatedMealIngredientDomain(
    val foodItemId: String,
    val czName: String,
    val engName: String,
    val grams: Double,
    val nutrition: FoodNutritionValues,
)

data class MyCreatedMealDomain(
    val id: String,
    val name: String,
    val ingredients: List<MyCreatedMealIngredientDomain>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val portions: List<FoodPortionDomain> = emptyList(),
) {

    // MARK: - Functions

    fun asFoodItem(): FoodItemDomain {
        val gramsList = ingredients.map { it.grams }
        val totalGrams = gramsList.sum()
        fun density(value: (FoodNutritionValues) -> Double): Double =
            weightedMeanPerHundredGrams(values = ingredients.map { value(it.nutrition) }, grams = gramsList)
        fun densityOptional(value: (FoodNutritionValues) -> Double?): Double? {
            val values = ingredients.map { value(it.nutrition) ?: return null }
            return weightedMeanPerHundredGrams(values = values, grams = gramsList)
        }
        return FoodItemDomain(
            id = id,
            kind = FoodItemKind.CREATED_MEAL,
            czName = name,
            engName = "",
            weight = totalGrams,
            date = createdAt,
            energyKJ = density { it.energyKJ },
            caloriesPerHundredGrams = density { it.caloriesPerHundredGrams },
            fat = density { it.fat },
            fatSaturated = densityOptional { it.fatSaturated },
            fatUnsaturatedFattyAcids = density { it.fatUnsaturatedFattyAcids },
            carbohydrate = density { it.carbohydrate },
            carbohydratePureSugar = density { it.carbohydratePureSugar },
            fiber = densityOptional { it.fiber },
            protein = density { it.protein },
            salt = density { it.salt },
            portions = portions,
        )
    }
}

sealed class MyCreatedMealError : Exception() {
    data object InvalidName : MyCreatedMealError()
    data object NoIngredients : MyCreatedMealError()
    data object InvalidIngredientWeight : MyCreatedMealError()
    data object InvalidPortion : MyCreatedMealError()
}

object MyCreatedMealValidation {

    // MARK: - Functions

    fun validate(
        name: String,
        ingredients: List<MyCreatedMealIngredientDomain>,
        portions: List<FoodPortionDomain> = emptyList(),
    ): MyCreatedMealError? {
        if (name.isBlank()) return MyCreatedMealError.InvalidName
        if (ingredients.isEmpty()) return MyCreatedMealError.NoIngredients
        if (ingredients.any { it.grams < 1 }) return MyCreatedMealError.InvalidIngredientWeight
        if (portions.any { FoodPortionValidation.validate(name = it.name, grams = it.grams) != null }) return MyCreatedMealError.InvalidPortion
        if (FoodPortionValidation.validate(portions) != null) return MyCreatedMealError.InvalidPortion
        return null
    }

    fun canSave(
        name: String,
        ingredients: List<MyCreatedMealIngredientDomain>,
        portions: List<FoodPortionDomain> = emptyList(),
    ): Boolean = validate(name = name, ingredients = ingredients, portions = portions) == null
}
