package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemReportDomain

data class FetchMyFoodItemReportUseCaseFake(
    val stubbedReport: FoodItemReportDomain? = null,
    val errorToThrow: Exception? = null,
) : FetchMyFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemReportDomain? {
        errorToThrow?.let { throw it }
        return stubbedReport
    }
}
