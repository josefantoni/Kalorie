package antoni.kalorie.core.usecases

data class DeleteFoodItemReportUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, reportedBy: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
