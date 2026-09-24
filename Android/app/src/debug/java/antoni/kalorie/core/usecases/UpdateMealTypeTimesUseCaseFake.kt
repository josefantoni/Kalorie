package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MealTypeDomain

data class UpdateMealTypeTimesUseCaseFake(
    val shouldThrow: Boolean = false,
) : UpdateMealTypeTimesUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(mealTypes: List<MealTypeDomain>) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
