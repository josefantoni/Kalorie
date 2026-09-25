package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.instantFromEpochSeconds

interface RefreshFavouriteFoodUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain): FoodItemDomain
}

class RefreshFavouriteFoodUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : RefreshFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemDomain {
        if (item.kind != FoodItemKind.CATALOGUE) return item
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val catalogueDTO: FoodItemDTO? = dataProvider.loadAsync(id = item.id, from = Constants.Firestore.FOOD_ITEMS)
        val fresh = catalogueDTO?.asDomain()
        if (fresh == null || fresh == item) return item
        val collection = Constants.Firestore.favouriteFoods(userId)
        val existing: FavouriteFoodDTO = dataProvider.loadAsync(id = item.id, from = collection) ?: return fresh
        val dto = FavouriteFoodDTO(item = fresh, favouritedAt = instantFromEpochSeconds(existing.favouritedAt))
        dataProvider.setAsync(dto, id = item.id, inCollection = collection)
        return fresh
    }
}
