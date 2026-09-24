package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MealTypeDomain

data class DeleteMealTypeUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteMealTypeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(mealType: MealTypeDomain) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
