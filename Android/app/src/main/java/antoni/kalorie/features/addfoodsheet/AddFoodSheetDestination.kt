package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.core.models.FoodItemDomain

sealed interface AddFoodSheetDestination {
    data object Search : AddFoodSheetDestination
    data class FoodQuantity(val item: FoodItemDomain) : AddFoodSheetDestination
}
