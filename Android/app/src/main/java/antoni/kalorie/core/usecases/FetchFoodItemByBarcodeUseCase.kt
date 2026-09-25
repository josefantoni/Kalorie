package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchFoodItemByBarcodeUseCaseProtocol {
    suspend operator fun invoke(barcode: String): FoodItemDomain?
}

class FetchFoodItemByBarcodeUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
) : FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemDomain? {
        if (barcode.isEmpty()) return null
        val dto: FoodItemDTO? = dataProvider.loadAsync(id = barcode, from = Constants.Firestore.FOOD_ITEMS)
        return dto?.asDomain()
    }
}
