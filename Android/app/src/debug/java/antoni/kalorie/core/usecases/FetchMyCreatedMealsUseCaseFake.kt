package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MyCreatedMealDomain

data class FetchMyCreatedMealsUseCaseFake(
    val stubbedMeals: List<MyCreatedMealDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchMyCreatedMealsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MyCreatedMealDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedMeals
    }
}
