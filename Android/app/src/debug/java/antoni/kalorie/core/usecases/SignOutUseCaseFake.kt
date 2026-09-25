package antoni.kalorie.core.usecases

data class SignOutUseCaseFake(
    val shouldThrow: Boolean = false,
) : SignOutUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
