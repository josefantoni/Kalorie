package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodPortionDomain

data class SaveFoodItemPersonalPortionsUseCaseFake(
    val shouldThrow: Boolean = false,
) : SaveFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, portions: List<FoodPortionDomain>) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
