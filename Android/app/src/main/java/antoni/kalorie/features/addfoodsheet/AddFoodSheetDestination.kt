package antoni.kalorie.features.addfoodsheet

sealed interface AddFoodSheetDestination {
    data object Search : AddFoodSheetDestination
}
