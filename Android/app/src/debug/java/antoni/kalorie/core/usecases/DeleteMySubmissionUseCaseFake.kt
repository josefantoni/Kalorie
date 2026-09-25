package antoni.kalorie.core.usecases

data class DeleteMySubmissionUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteMySubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
