package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.TipProductDomain
import antoni.kalorie.core.tips.TipStoreProtocol

interface LoadTipProductsUseCaseProtocol {
    suspend operator fun invoke(): List<TipProductDomain>
}

class LoadTipProductsUseCase(private val store: TipStoreProtocol) : LoadTipProductsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<TipProductDomain> = store.products()
}
