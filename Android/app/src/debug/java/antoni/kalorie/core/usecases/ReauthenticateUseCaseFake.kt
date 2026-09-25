package antoni.kalorie.core.usecases

data class ReauthenticateUseCaseFake(
    val errorToThrow: Exception? = null,
) : ReauthenticateUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        errorToThrow?.let { throw it }
    }
}
