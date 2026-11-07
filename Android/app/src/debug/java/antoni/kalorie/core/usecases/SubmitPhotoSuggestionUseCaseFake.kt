package antoni.kalorie.core.usecases

data class SubmitPhotoSuggestionUseCaseFake(
    val errorToThrow: Exception? = null,
) : SubmitPhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(itemId: String, photo: ByteArray) {
        errorToThrow?.let { throw it }
    }
}
