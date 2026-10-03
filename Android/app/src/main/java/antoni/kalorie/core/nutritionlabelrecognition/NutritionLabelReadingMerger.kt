package antoni.kalorie.core.nutritionlabelrecognition

object NutritionLabelReadingMerger {

    // MARK: - Properties

    private const val MIN_AGREEMENT = 2

    // MARK: - Functions

    fun merge(readings: List<NutritionLabelReading>): NutritionLabelReading = NutritionLabelReading(
        energyKJ = agreed(readings) { it.energyKJ },
        caloriesPerHundredGrams = agreed(readings) { it.caloriesPerHundredGrams },
        fat = agreed(readings) { it.fat },
        fatSaturated = agreed(readings) { it.fatSaturated },
        fatUnsaturatedFattyAcids = agreed(readings) { it.fatUnsaturatedFattyAcids },
        carbohydrate = agreed(readings) { it.carbohydrate },
        carbohydratePureSugar = agreed(readings) { it.carbohydratePureSugar },
        fiber = agreed(readings) { it.fiber },
        protein = agreed(readings) { it.protein },
        salt = agreed(readings) { it.salt },
        measure = agreed(readings) { it.measure },
    )

    // OCR errors differ from frame to frame, so a value read identically in several frames is far more
    // likely correct than one that appeared once.
    private fun <T : Any> agreed(readings: List<NutritionLabelReading>, field: (NutritionLabelReading) -> T?): T? = readings
        .mapNotNull(field)
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= MIN_AGREEMENT }
        .maxByOrNull { it.value }
        ?.key
}
