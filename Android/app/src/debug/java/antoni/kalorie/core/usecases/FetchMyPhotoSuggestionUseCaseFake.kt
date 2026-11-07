package antoni.kalorie.core.usecases

data class FetchMyPhotoSuggestionUseCaseFake(
    val hasSuggestion: Boolean = false,
    val errorToThrow: Exception? = null,
) : FetchMyPhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(itemId: String): Boolean {
        errorToThrow?.let { throw it }
        return hasSuggestion
    }
}
