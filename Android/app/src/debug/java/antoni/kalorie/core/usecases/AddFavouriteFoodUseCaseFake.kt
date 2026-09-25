package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class AddFavouriteFoodUseCaseFake(
    val shouldThrow: Boolean = false,
) : AddFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
