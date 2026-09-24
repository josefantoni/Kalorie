package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MealTypeDomain

data class FetchMealTypesUseCaseFake(
    val stubbedTypes: List<MealTypeDomain> = emptyList(),
    val shouldThrow: Boolean = false,
    val errorToThrow: Exception = RuntimeException("unknown"),
) : FetchMealTypesUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MealTypeDomain> {
        if (shouldThrow) throw errorToThrow
        return stubbedTypes
    }
}
