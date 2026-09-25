package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodPortionDomain

data class FetchFoodItemPersonalPortionsUseCaseFake(
    val stubbedPortions: List<FoodPortionDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): List<FoodPortionDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedPortions
    }
}
