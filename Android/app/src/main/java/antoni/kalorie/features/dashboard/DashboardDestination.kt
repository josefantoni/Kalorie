package antoni.kalorie.features.dashboard

import antoni.kalorie.core.models.FoodConsumedDomain

sealed interface DashboardDestination {
    data object Dashboard : DashboardDestination
    data class FoodConsumedDetail(val food: FoodConsumedDomain) : DashboardDestination
}
