package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCase
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCase
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCase
import antoni.kalorie.core.usecases.FetchMealTypesUseCase
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCase

class DashboardConfigurator {

    // MARK: - Functions

    @Composable
    fun createView(userId: String?) {
        val mealNames = listOf(
            stringResource(R.string.defaultMeals_breakfast),
            stringResource(R.string.defaultMeals_secondBreakfast),
            stringResource(R.string.defaultMeals_lunch),
            stringResource(R.string.defaultMeals_snack),
            stringResource(R.string.defaultMeals_dinner),
        )
        val viewModel = viewModel(key = userId) {
            val dataProvider = FirestoreDataProvider()
            val authProvider = AuthProvider()
            DashboardViewModel(
                fetchMealTypes = FetchMealTypesUseCase(dataProvider, authProvider),
                fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCase(dataProvider, authProvider),
                setupDefaultMeals = SetupDefaultMealsUseCase(dataProvider, authProvider, mealNames),
                confirmMealTypesEmpty = ConfirmMealTypesEmptyUseCase(dataProvider, authProvider),
                deleteFoodConsumed = DeleteFoodConsumedUseCase(dataProvider, authProvider),
            )
        }
        DashboardView(viewModel = viewModel)
    }
}
