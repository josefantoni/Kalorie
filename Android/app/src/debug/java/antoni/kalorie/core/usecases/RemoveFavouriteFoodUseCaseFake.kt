package antoni.kalorie.core.usecases

data class RemoveFavouriteFoodUseCaseFake(
    val shouldThrow: Boolean = false,
) : RemoveFavouriteFoodUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
