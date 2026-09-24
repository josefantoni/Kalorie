package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.features.mealtypesheet.MealTypeSheetConfigurator

class DashboardRouter(
    private val mealTypeSheetConfigurator: MealTypeSheetConfigurator,
) {

    // MARK: - Functions

    @Composable
    fun makeMealTypeSheetView(mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onMealTypesChanged: () -> Unit = {}) {
        mealTypeSheetConfigurator.createView(mealTypes = mealTypes, onDismiss = onDismiss, onMealTypesChanged = onMealTypesChanged)
    }
}
