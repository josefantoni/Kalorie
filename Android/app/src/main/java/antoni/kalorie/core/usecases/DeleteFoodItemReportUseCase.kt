package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface DeleteFoodItemReportUseCaseProtocol {
    suspend operator fun invoke(barcode: String, reportedBy: String)
}

class DeleteFoodItemReportUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, reportedBy: String) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        dataProvider.deleteAsync(
            id = FoodItemReportDomain.id(barcode = barcode, userId = reportedBy),
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
        )
    }
}
