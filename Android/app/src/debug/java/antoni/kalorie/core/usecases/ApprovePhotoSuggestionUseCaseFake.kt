package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.PhotoSuggestionDomain

data class ApprovePhotoSuggestionUseCaseFake(
    val errorToThrow: Exception? = null,
) : ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(suggestion: PhotoSuggestionDomain) {
        errorToThrow?.let { throw it }
    }
}
