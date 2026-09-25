package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class RefreshFavouriteFoodUseCaseFake(
    val stubbedItem: FoodItemDomain? = null,
    val shouldThrow: Boolean = false,
) : RefreshFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemDomain {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedItem ?: item
    }
}
