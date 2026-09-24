package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.features.addfoodsheet.AddFoodSheetConfigurator
import antoni.kalorie.features.mealtypesheet.MealTypeSheetConfigurator

class DashboardRouter(
    private val mealTypeSheetConfigurator: MealTypeSheetConfigurator,
    private val addFoodSheetConfigurator: AddFoodSheetConfigurator,
    private val foodConsumedDetailConfigurator: FoodConsumedDetailConfigurator,
) {

    // MARK: - Functions

    @Composable
    fun makeMealTypeSheetView(mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onMealTypesChanged: () -> Unit = {}) {
        mealTypeSheetConfigurator.createView(mealTypes = mealTypes, onDismiss = onDismiss, onMealTypesChanged = onMealTypesChanged)
    }

    @Composable
    fun makeAddFoodSheetView(onDismiss: () -> Unit) {
        addFoodSheetConfigurator.createView(onDismiss = onDismiss)
    }

    @Composable
    fun makeFoodConsumedDetailView(
        food: FoodConsumedDomain,
        mealTypes: List<MealTypeDomain>,
        onBack: () -> Unit,
        onFoodUpdated: () -> Unit = {},
    ) {
        foodConsumedDetailConfigurator.createView(food = food, mealTypes = mealTypes, onBack = onBack, onFoodUpdated = onFoodUpdated)
    }
}
