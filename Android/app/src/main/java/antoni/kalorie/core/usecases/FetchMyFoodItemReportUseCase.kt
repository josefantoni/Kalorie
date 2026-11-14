package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchMyFoodItemReportUseCaseProtocol {
    suspend operator fun invoke(barcode: String): FoodItemReportDomain?
}

class FetchMyFoodItemReportUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchMyFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemReportDomain? {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos: List<FoodItemReportDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
            whereEqualTo = listOf(
                Constants.Firestore.Field.REPORTED_BY to userId,
                Constants.Firestore.Field.BARCODE to barcode,
                Constants.Firestore.Field.STATUS to Constants.Firestore.PENDING_STATUS,
            ),
            limit = 1,
        )
        return dtos.firstOrNull()?.asDomain()
    }
}
