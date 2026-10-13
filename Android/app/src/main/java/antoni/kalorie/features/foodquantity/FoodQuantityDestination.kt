package antoni.kalorie.features.foodquantity

sealed interface FoodQuantityDestination {
    data object Quantity : FoodQuantityDestination
    data object PortionsManager : FoodQuantityDestination
    data object MealEditor : FoodQuantityDestination
}
