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
        val dto: FoodItemReportDTO? = dataProvider.loadAsync(
            id = FoodItemReportDomain.id(barcode = barcode, userId = userId),
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
        )
        return dto?.asDomain()
    }
}
