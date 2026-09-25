package antoni.kalorie.core.usecases

data class DeleteMyCreatedMealUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteMyCreatedMealUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
