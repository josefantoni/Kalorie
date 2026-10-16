package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.TipProductDomain

data class LoadTipProductsUseCaseFake(
    val stubbedProducts: List<TipProductDomain> = emptyList(),
    val stubbedError: Exception? = null,
) : LoadTipProductsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<TipProductDomain> {
        stubbedError?.let { throw it }
        return stubbedProducts
    }
}
