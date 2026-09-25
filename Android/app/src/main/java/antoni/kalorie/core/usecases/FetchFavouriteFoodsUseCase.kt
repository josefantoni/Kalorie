package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchFavouriteFoodsUseCaseProtocol {
    suspend operator fun invoke(): List<FoodItemDomain>
}

class FetchFavouriteFoodsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchFavouriteFoodsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos: List<FavouriteFoodDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.favouriteFoods(userId),
            orderBy = "favourited_at",
            descending = true,
            limit = 50,
        )
        return dtos.map { it.asDomain() }
    }
}
