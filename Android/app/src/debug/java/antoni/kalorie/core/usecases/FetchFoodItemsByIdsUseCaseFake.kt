package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class FetchFoodItemsByIdsUseCaseFake(
    val stubbedItems: List<FoodItemDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchFoodItemsByIdsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(ids: List<String>): List<FoodItemDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedItems
    }
}
