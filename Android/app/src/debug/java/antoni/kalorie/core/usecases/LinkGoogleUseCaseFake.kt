package antoni.kalorie.core.usecases

data class LinkGoogleUseCaseFake(
    val errorToThrow: Exception? = null,
) : LinkGoogleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        errorToThrow?.let { throw it }
    }
}
