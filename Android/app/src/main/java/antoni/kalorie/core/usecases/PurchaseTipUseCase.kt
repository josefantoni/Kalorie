package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.TipPurchaseOutcome
import antoni.kalorie.core.tips.TipStoreProtocol

interface PurchaseTipUseCaseProtocol {
    suspend operator fun invoke(productId: String): TipPurchaseOutcome
}

class PurchaseTipUseCase(private val store: TipStoreProtocol) : PurchaseTipUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(productId: String): TipPurchaseOutcome = store.purchase(productId)
}
