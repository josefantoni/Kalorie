package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.PhotoSuggestionDomain

data class FetchPhotoSuggestionsUseCaseFake(
    val stubbedSuggestions: List<PhotoSuggestionDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchPhotoSuggestionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<PhotoSuggestionDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedSuggestions
    }
}
