package antoni.kalorie.core.usecases

data class SignInWithGoogleUseCaseFake(
    val errorToThrow: Exception? = null,
) : SignInWithGoogleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        errorToThrow?.let { throw it }
    }
}
