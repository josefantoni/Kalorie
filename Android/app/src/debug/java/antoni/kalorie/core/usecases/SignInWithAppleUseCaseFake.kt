package antoni.kalorie.core.usecases

data class SignInWithAppleUseCaseFake(
    val errorToThrow: Exception? = null,
) : SignInWithAppleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        errorToThrow?.let { throw it }
    }
}
