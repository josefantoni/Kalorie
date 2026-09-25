package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.components.FoodPortionDraft
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
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

    // A field an earlier scan already filled is tracked in alreadyRecognizedFields, not by checking
    // for zero: a legitimately zero macro would otherwise look "never set" and a noisier rescan
    // could silently overwrite it.
    fun applying(
        reading: NutritionLabelReading,
        alreadyRecognizedFields: Set<FoodItemFormField> = emptySet(),
    ): Pair<FoodItemFormInput, Set<FoodItemFormField>> {
        var result = this
        val applied = mutableSetOf<FoodItemFormField>()

        fun fillIfEmpty(current: Double?, value: Double?, field: FoodItemFormField, assign: FoodItemFormInput.(Double) -> FoodItemFormInput) {
            if (field in alreadyRecognizedFields || (current ?: 0.0) != 0.0 || value == null) return
            result = result.assign(value)
            applied.add(field)
        }

        val code = reading.scannedCode
        if (scannedCode.isEmpty() && code != null) result = result.copy(scannedCode = code)
        val readName = reading.name
        if (name.isEmpty() && readName != null) {
            result = result.copy(name = readName)
            applied.add(FoodItemFormField.NAME)
        }
        val readWeight = reading.weightOfProduct
        if (weightOfProduct == 0.0 && readWeight != null) {
            val (display, isInThousands) = weightDisplay(readWeight)
            result = result.copy(weightOfProduct = display, isWeightInThousands = isInThousands)
            applied.add(FoodItemFormField.WEIGHT)
        }
        val readMeasure = reading.measure
        if (measure == FoodMeasure.GRAMS && readMeasure != null) {
            result = result.copy(measure = readMeasure)
            applied.add(FoodItemFormField.MEASURE)
        }
        fillIfEmpty(energyKJ, reading.energyKJ, FoodItemFormField.ENERGY_KJ) { copy(energyKJ = it) }
        fillIfEmpty(caloriesPerHundredGrams, reading.caloriesPerHundredGrams, FoodItemFormField.CALORIES) { copy(caloriesPerHundredGrams = it) }
        fillIfEmpty(fat, reading.fat, FoodItemFormField.FAT) { copy(fat = it) }
        fillIfEmpty(fatSaturated, reading.fatSaturated, FoodItemFormField.FAT_SATURATED) { copy(fatSaturated = it) }
        fillIfEmpty(fatUnsaturatedFattyAcids, reading.fatUnsaturatedFattyAcids, FoodItemFormField.FAT_UNSATURATED) {
            copy(fatUnsaturatedFattyAcids = it)
        }
        fillIfEmpty(carbohydrate, reading.carbohydrate, FoodItemFormField.CARBOHYDRATE) { copy(carbohydrate = it) }
        fillIfEmpty(carbohydratePureSugar, reading.carbohydratePureSugar, FoodItemFormField.CARBOHYDRATE_SUGAR) {
            copy(carbohydratePureSugar = it)
        }
        fillIfEmpty(fiber, reading.fiber, FoodItemFormField.FIBER) { copy(fiber = it) }
        fillIfEmpty(protein, reading.protein, FoodItemFormField.PROTEIN) { copy(protein = it) }
        fillIfEmpty(salt, reading.salt, FoodItemFormField.SALT) { copy(salt = it) }
        val readPortions = reading.portions
        if (isPortionsEmpty && !readPortions.isNullOrEmpty()) {
            result = result.copy(portions = readPortions.map { FoodPortionDraft(name = it.name, gramsText = formattedGrams(it.grams)) })
        }
        return result to applied
    }

    private val isPortionsEmpty: Boolean
        get() = portions.all { it.name.isBlank() && it.gramsText.isEmpty() }

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
