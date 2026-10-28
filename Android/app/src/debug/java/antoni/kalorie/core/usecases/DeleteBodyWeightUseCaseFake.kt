package antoni.kalorie.core.usecases

data class DeleteBodyWeightUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteBodyWeightUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(dayKey: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
