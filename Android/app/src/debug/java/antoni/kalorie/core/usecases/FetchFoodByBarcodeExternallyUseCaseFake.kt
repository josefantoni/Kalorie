package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain

data class FetchFoodByBarcodeExternallyUseCaseFake(
    val stubbedItem: FoodItemDomain? = null,
    val shouldThrow: Boolean = false,
) : FetchFoodByBarcodeExternallyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemDomain? {
        if (shouldThrow) throw FetchFoodByBarcodeExternallyError.InvalidURL
        return stubbedItem
    }
}
