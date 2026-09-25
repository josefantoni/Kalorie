package antoni.kalorie.core.nutritionlabelrecognition

import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.FoodPortionDomain

data class NutritionLabelReading(
    val scannedCode: String? = null,
    val name: String? = null,
    val weightOfProduct: Double? = null,
    val energyKJ: Double? = null,
    val caloriesPerHundredGrams: Double? = null,
    val fat: Double? = null,
    val fatSaturated: Double? = null,
    val fatUnsaturatedFattyAcids: Double? = null,
    val carbohydrate: Double? = null,
    val carbohydratePureSugar: Double? = null,
    val fiber: Double? = null,
    val protein: Double? = null,
    val salt: Double? = null,
    val portions: List<FoodPortionDomain>? = null,
    val measure: FoodMeasure? = null,
) {

    // MARK: - Properties

    val recognizedFields: Set<FoodItemFormField>
        get() = buildSet {
            if (name != null) add(FoodItemFormField.NAME)
            if (weightOfProduct != null) add(FoodItemFormField.WEIGHT)
            if (energyKJ != null) add(FoodItemFormField.ENERGY_KJ)
            if (caloriesPerHundredGrams != null) add(FoodItemFormField.CALORIES)
            if (fat != null) add(FoodItemFormField.FAT)
            if (fatSaturated != null) add(FoodItemFormField.FAT_SATURATED)
            if (fatUnsaturatedFattyAcids != null) add(FoodItemFormField.FAT_UNSATURATED)
            if (carbohydrate != null) add(FoodItemFormField.CARBOHYDRATE)
            if (carbohydratePureSugar != null) add(FoodItemFormField.CARBOHYDRATE_SUGAR)
            if (fiber != null) add(FoodItemFormField.FIBER)
            if (protein != null) add(FoodItemFormField.PROTEIN)
            if (salt != null) add(FoodItemFormField.SALT)
            if (measure != null) add(FoodItemFormField.MEASURE)
        }

    val isEmpty: Boolean
        get() = recognizedFields.isEmpty() && scannedCode == null && (portions?.isEmpty() ?: true)

    val isCompleteForAutoCapture: Boolean
        get() = caloriesPerHundredGrams != null && fat != null && carbohydrate != null && protein != null
}
