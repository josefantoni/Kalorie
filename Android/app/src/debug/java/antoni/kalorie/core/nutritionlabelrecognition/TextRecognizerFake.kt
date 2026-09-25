package antoni.kalorie.core.nutritionlabelrecognition

data class TextRecognizerFake(
    val stubbedLines: List<RecognizedTextLine> = emptyList(),
    val errorToThrow: Exception? = null,
) : TextRecognizerProtocol {

    // MARK: - Functions

    override suspend fun recognizeText(image: NutritionLabelImage): List<RecognizedTextLine> {
        errorToThrow?.let { throw it }
        return stubbedLines
    }
}
