package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.components.FoodPortionDraft
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.FoodPortionDomain
import java.time.Instant

data class FoodItemFormInput(
    val scannedCode: String = "",
    val name: String = "",
    val engName: String = "",
    val weightOfProduct: Double = 0.0,
    val energyKJ: Double = 0.0,
    val caloriesPerHundredGrams: Double = 0.0,
    val fat: Double = 0.0,
    val fatSaturated: Double? = 0.0,
    val fatUnsaturatedFattyAcids: Double = 0.0,
    val carbohydrate: Double = 0.0,
    val carbohydratePureSugar: Double = 0.0,
    val fiber: Double? = 0.0,
    val protein: Double = 0.0,
    val salt: Double = 0.0,
    val portions: List<FoodPortionDraft> = listOf(FoodPortionDraft.blank),
    val measure: FoodMeasure = FoodMeasure.GRAMS,
    val isWeightInThousands: Boolean = false,
) {

    // MARK: - Functions

    fun asFoodItemDomain(kind: FoodItemKind = FoodItemKind.CATALOGUE, date: Instant = Instant.now()): FoodItemDomain = FoodItemDomain(
        id = scannedCode,
        kind = kind,
        czName = name,
        engName = engName,
        weight = if (isWeightInThousands) weightOfProduct * 1000 else weightOfProduct,
        date = date,
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
        portions = parsedPortions(portions),
        measure = measure,
    )

    companion object {
        fun from(item: FoodItemDomain): FoodItemFormInput {
            val weightDisplay = weightDisplay(item.weight)
            return FoodItemFormInput(
                scannedCode = item.barcode ?: "",
                name = item.czName,
                engName = item.engName,
                weightOfProduct = weightDisplay.first,
                energyKJ = item.energyKJ,
                caloriesPerHundredGrams = item.caloriesPerHundredGrams,
                fat = item.fat,
                fatSaturated = item.fatSaturated,
                fatUnsaturatedFattyAcids = item.fatUnsaturatedFattyAcids,
                carbohydrate = item.carbohydrate,
                carbohydratePureSugar = item.carbohydratePureSugar,
                fiber = item.fiber,
                protein = item.protein,
                salt = item.salt,
                portions = item.portions.ifEmpty { null }
                    ?.map { FoodPortionDraft(name = it.name, gramsText = formattedGrams(it.grams)) }
                    ?: listOf(FoodPortionDraft.blank),
                measure = item.measure,
                isWeightInThousands = weightDisplay.second,
            )
        }

        fun weightDisplay(weight: Double): Pair<Double, Boolean> = if (weight >= 1000) weight / 1000 to true else weight to false

        fun parsedPortions(drafts: List<FoodPortionDraft>): List<FoodPortionDomain> = drafts.mapNotNull { draft ->
            val grams = draft.gramsText.replace(',', '.').toDoubleOrNull() ?: 0.0
            if (grams >= 1) FoodPortionDomain(name = draft.name, grams = grams) else null
        }

        private fun formattedGrams(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}
