package antoni.kalorie.core.usecases

data class DeleteFoodConsumedUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteFoodConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
