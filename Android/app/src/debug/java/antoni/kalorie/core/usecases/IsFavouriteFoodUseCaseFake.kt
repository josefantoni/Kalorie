package antoni.kalorie.core.usecases

data class IsFavouriteFoodUseCaseFake(
    val stubbedResult: Boolean = false,
    val shouldThrow: Boolean = false,
) : IsFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String): Boolean {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedResult
    }
}
