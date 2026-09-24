package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodConsumedDomain

data class UpdateFoodConsumedUseCaseFake(
    val shouldThrow: Boolean = false,
) : UpdateFoodConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(food: FoodConsumedDomain, newWeight: Double) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
