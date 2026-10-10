package antoni.kalorie.core.usecases

data class DeleteFoodPhotoUseCaseFake(
    val shouldThrow: Boolean = false,
) : DeleteFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(url: String) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
