package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto

data class UpdateFoodItemUseCaseFake(
    val errorToThrow: Exception? = null,
) : UpdateFoodItemUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Boolean) {
        errorToThrow?.let { throw it }
    }
}
