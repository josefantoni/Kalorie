package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodConsumedDomain

data class AssignFoodMealTypeUseCaseFake(
    val shouldThrow: Boolean = false,
) : AssignFoodMealTypeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(food: FoodConsumedDomain, mealTypeId: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
