package antoni.kalorie.features.moderation

import antoni.kalorie.core.models.FoodItemReportDomain

sealed interface ModerationReportsDestination {
    data object Reports : ModerationReportsDestination
    data class CatalogueEditor(val report: FoodItemReportDomain) : ModerationReportsDestination
}
