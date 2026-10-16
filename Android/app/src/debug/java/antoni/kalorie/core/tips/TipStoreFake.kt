package antoni.kalorie.core.tips

import antoni.kalorie.core.models.TipProductDomain
import antoni.kalorie.core.models.TipPurchaseOutcome
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.delay

class TipStoreFake(
    private val stubbedPrices: Map<String, String> = mapOf(
        Constants.Tips.SMALL_PRODUCT_ID to "29 Kč",
        Constants.Tips.MEDIUM_PRODUCT_ID to "79 Kč",
        Constants.Tips.LARGE_PRODUCT_ID to "199 Kč",
    ),
    private val stubbedOutcome: TipPurchaseOutcome = TipPurchaseOutcome.PURCHASED,
    private val latencyMillis: Long = 400,
) : TipStoreProtocol {

    // MARK: - Functions

    override suspend fun products(): List<TipProductDomain> {
        delay(latencyMillis)
        return Constants.Tips.productIds.mapNotNull { id ->
            stubbedPrices[id]?.let { TipProductDomain(id = id, displayPrice = it) }
        }
    }

    override suspend fun purchase(productId: String): TipPurchaseOutcome {
        delay(latencyMillis)
        return stubbedOutcome
    }
}
