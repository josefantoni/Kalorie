package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.FoodPortionValidation
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.FoodPortionDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants

interface SaveFoodItemPersonalPortionsUseCaseProtocol {
    suspend operator fun invoke(barcode: String, portions: List<FoodPortionDomain>)
}

class SaveFoodItemPersonalPortionsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SaveFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, portions: List<FoodPortionDomain>) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        for (portion in portions) {
            FoodPortionValidation.validate(name = portion.name, grams = portion.grams)?.let { throw it }
        }
        FoodPortionValidation.validate(portions)?.let { throw it }
        val dto = FoodItemPersonalPortionsDTO(id = barcode, portions = portions.map(::FoodPortionDTO))
        dataProvider.setAsync(dto, id = barcode, inCollection = Constants.Firestore.foodItemPortions(userId))
    }
}
