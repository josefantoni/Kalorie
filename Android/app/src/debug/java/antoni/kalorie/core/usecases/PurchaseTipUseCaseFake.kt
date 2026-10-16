package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.TipPurchaseOutcome

data class PurchaseTipUseCaseFake(
    val stubbedOutcome: TipPurchaseOutcome = TipPurchaseOutcome.PURCHASED,
    val stubbedError: Exception? = null,
) : PurchaseTipUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(productId: String): TipPurchaseOutcome {
        stubbedError?.let { throw it }
        return stubbedOutcome
    }
}
