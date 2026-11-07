package antoni.kalorie.features.notifications

import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain

sealed interface NotificationsDestination {
    data object List : NotificationsDestination
    data class Submission(val rowId: String, val submission: FoodItemSubmissionDomain) : NotificationsDestination
    data class Report(val rowId: String, val barcode: String, val reports: kotlin.collections.List<FoodItemReportDomain>) : NotificationsDestination
    data class PhotoSuggestion(val rowId: String, val suggestion: PhotoSuggestionDomain) : NotificationsDestination
}
