package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface IsFavouriteFoodUseCaseProtocol {
    suspend operator fun invoke(id: String): Boolean
}

class IsFavouriteFoodUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : IsFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String): Boolean {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto: FavouriteFoodDTO? = dataProvider.loadAsync(id = id, from = Constants.Firestore.favouriteFoods(userId))
        return dto != null
    }
}
