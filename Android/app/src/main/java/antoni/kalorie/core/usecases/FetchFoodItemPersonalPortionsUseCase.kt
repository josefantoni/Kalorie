package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchFoodItemPersonalPortionsUseCaseProtocol {
    suspend operator fun invoke(barcode: String): List<FoodPortionDomain>
}

class FetchFoodItemPersonalPortionsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): List<FoodPortionDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto: FoodItemPersonalPortionsDTO? = dataProvider.loadAsync(id = barcode, from = Constants.Firestore.foodItemPortions(userId))
        return dto?.portions?.map { it.asDomain() } ?: emptyList()
    }
}
