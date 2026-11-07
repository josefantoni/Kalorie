package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.PhotoSuggestionDomain

data class RejectPhotoSuggestionUseCaseFake(
    val errorToThrow: Exception? = null,
) : RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(suggestion: PhotoSuggestionDomain) {
        errorToThrow?.let { throw it }
    }
}
