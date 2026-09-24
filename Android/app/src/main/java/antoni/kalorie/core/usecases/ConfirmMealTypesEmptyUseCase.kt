package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.utils.Constants

interface ConfirmMealTypesEmptyUseCaseProtocol {
    suspend operator fun invoke(): Boolean
}

class ConfirmMealTypesEmptyUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : ConfirmMealTypesEmptyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos: List<MealTypeDTO> = dataProvider.loadFromServerAsync(Constants.Firestore.mealTypes(userId))
        return dtos.isEmpty()
    }
}
