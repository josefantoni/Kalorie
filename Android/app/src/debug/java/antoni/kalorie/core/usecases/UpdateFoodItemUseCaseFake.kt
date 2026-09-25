package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class UpdateFoodItemUseCaseFake(
    val errorToThrow: Exception? = null,
) : UpdateFoodItemUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain) {
        errorToThrow?.let { throw it }
    }
}
