package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemReportDomain

data class FetchFoodItemReportsUseCaseFake(
    val stubbedReports: List<FoodItemReportDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchFoodItemReportsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemReportDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedReports
    }
}
