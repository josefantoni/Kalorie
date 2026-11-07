package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.PhotoSuggestionDomain

sealed interface ModerationPhotoSuggestionsDestination {
    data object Suggestions : ModerationPhotoSuggestionsDestination
    data class Review(val suggestion: PhotoSuggestionDomain) : ModerationPhotoSuggestionsDestination
}
