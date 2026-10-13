package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.FoodItemSubmissionDomain

sealed interface ModerationQueueDestination {
    data object Queue : ModerationQueueDestination
    data class Review(val submission: FoodItemSubmissionDomain) : ModerationQueueDestination
    data object CatalogueEditor : ModerationQueueDestination
}
