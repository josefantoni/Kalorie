package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface AddFavouriteFoodUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain)
}

class AddFavouriteFoodUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : AddFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto = FavouriteFoodDTO(item = item, favouritedAt = Instant.now())
        dataProvider.setAsync(dto, id = item.id, inCollection = Constants.Firestore.favouriteFoods(userId))
    }
}
