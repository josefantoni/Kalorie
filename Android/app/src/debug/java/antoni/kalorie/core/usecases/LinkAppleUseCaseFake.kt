package antoni.kalorie.core.usecases

data class LinkAppleUseCaseFake(
    val errorToThrow: Exception? = null,
) : LinkAppleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        errorToThrow?.let { throw it }
    }
}
