package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class SearchFoodExternallyUseCaseFake(
    val shouldThrow: Boolean = false,
    val stubbedItems: List<FoodItemDomain> = emptyList(),
) : SearchFoodExternallyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(query: String): List<FoodItemDomain> {
        if (shouldThrow) throw SearchFoodExternallyError.InvalidURL
        return stubbedItems
    }
}
