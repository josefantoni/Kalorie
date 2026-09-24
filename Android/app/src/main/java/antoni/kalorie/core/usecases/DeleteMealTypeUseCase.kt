package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface DeleteMealTypeUseCaseProtocol {
    suspend operator fun invoke(mealType: MealTypeDomain)
}

class DeleteMealTypeUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : DeleteMealTypeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(mealType: MealTypeDomain) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        dataProvider.deleteAsync(id = mealType.id, from = Constants.Firestore.mealTypes(userId))
    }
}
