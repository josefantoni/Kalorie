package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchFoodItemReportsUseCaseProtocol {
    suspend operator fun invoke(): List<FoodItemReportDomain>
}

class FetchFoodItemReportsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchFoodItemReportsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemReportDomain> {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val dtos: List<FoodItemReportDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
            orderBy = "reported_at",
            descending = true,
            limit = Constants.Firestore.REPORTS_PAGE_LIMIT,
        )
        return dtos.map { it.asDomain() }
    }
}
