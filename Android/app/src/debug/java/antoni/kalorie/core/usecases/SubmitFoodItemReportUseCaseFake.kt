package antoni.kalorie.core.usecases

data class SubmitFoodItemReportUseCaseFake(
    val errorToThrow: Exception? = null,
) : SubmitFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, reason: String) {
        errorToThrow?.let { throw it }
    }
}
