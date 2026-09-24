package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class SearchFoodItemsUseCaseFake(
    val shouldThrow: Boolean = false,
    val stubbedItems: List<FoodItemDomain> = emptyList(),
) : SearchFoodItemsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(query: String): List<FoodItemDomain> {
        if (shouldThrow) throw RuntimeException("SearchFoodItemsUseCaseFake")
        return stubbedItems
    }
}
