package antoni.kalorie.core.nutritionlabelrecognition

data class BarcodeDetectorFake(
    val stubbedBarcode: String? = null,
    val errorToThrow: Exception? = null,
) : BarcodeDetectorProtocol {

    // MARK: - Functions

    override suspend fun detectBarcode(image: NutritionLabelImage): String? {
        errorToThrow?.let { throw it }
        return stubbedBarcode
    }
}
