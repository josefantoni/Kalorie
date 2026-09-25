package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface FetchFoodItemByBarcodeUseCaseProtocol {
    suspend operator fun invoke(barcode: String): FoodItemDomain?

    // A per-barcode fallback so fakes need not implement it; only the real use case batches.
    suspend operator fun invoke(barcodes: List<String>): List<FoodItemDomain> = coroutineScope {
        barcodes.map { barcode -> async { invoke(barcode) } }.awaitAll().filterNotNull()
    }
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

    override suspend fun invoke(barcodes: List<String>): List<FoodItemDomain> {
        val nonEmptyBarcodes = barcodes.filter { it.isNotEmpty() }.distinct()
        if (nonEmptyBarcodes.isEmpty()) return emptyList()
        val dtos: List<FoodItemDTO> = dataProvider.loadAsync(from = Constants.Firestore.FOOD_ITEMS, whereDocumentIdIn = nonEmptyBarcodes)
        return dtos.map { it.asDomain() }
    }
}
