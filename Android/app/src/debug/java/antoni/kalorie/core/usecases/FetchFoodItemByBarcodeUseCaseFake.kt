package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class FetchFoodItemByBarcodeUseCaseFake(
    val stubbedItem: FoodItemDomain? = null,
) : FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemDomain? = stubbedItem
}
