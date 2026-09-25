package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class FetchFavouriteFoodsUseCaseFake(
    val stubbedItems: List<FoodItemDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchFavouriteFoodsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedItems
    }
}
