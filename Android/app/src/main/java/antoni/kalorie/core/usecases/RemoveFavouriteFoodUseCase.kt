package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface RemoveFavouriteFoodUseCaseProtocol {
    suspend operator fun invoke(id: String)
}

class RemoveFavouriteFoodUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : RemoveFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        dataProvider.deleteAsync(id = id, from = Constants.Firestore.favouriteFoods(userId))
    }
}
