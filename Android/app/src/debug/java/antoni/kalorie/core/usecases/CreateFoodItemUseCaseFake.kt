package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

class CreateFoodItemUseCaseFake : CreateFoodItemUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemDomain = item
}
