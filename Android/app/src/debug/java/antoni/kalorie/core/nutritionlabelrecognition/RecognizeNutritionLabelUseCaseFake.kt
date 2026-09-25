package antoni.kalorie.core.nutritionlabelrecognition

data class RecognizeNutritionLabelUseCaseFake(
    val stubbedReading: NutritionLabelReading = NutritionLabelReading(),
    val errorToThrow: Exception? = null,
) : RecognizeNutritionLabelUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(image: NutritionLabelImage): NutritionLabelReading {
        errorToThrow?.let { throw it }
        return stubbedReading
    }
}
