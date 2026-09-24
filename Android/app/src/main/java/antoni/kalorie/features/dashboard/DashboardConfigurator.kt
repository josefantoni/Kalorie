package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCase
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCase
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCase
import antoni.kalorie.core.usecases.FetchMealTypesUseCase
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCase
import antoni.kalorie.features.addfoodsheet.AddFoodSheetConfigurator
import antoni.kalorie.features.mealtypesheet.MealTypeSheetConfigurator
import kotlinx.coroutines.launch

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
        val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
        val dataProvider = remember { FirestoreDataProvider() }
        val authProvider = remember { AuthProvider() }
        val viewModel = viewModel(key = "$userId/$languageTag") {
            DashboardViewModel(
                fetchMealTypes = FetchMealTypesUseCase(dataProvider, authProvider),
                fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCase(dataProvider, authProvider),
                setupDefaultMeals = SetupDefaultMealsUseCase(dataProvider, authProvider, mealNames),
                confirmMealTypesEmpty = ConfirmMealTypesEmptyUseCase(dataProvider, authProvider),
                deleteFoodConsumed = DeleteFoodConsumedUseCase(dataProvider, authProvider),
            )
        }
        val router = remember(dataProvider, authProvider) {
            DashboardRouter(
                mealTypeSheetConfigurator = MealTypeSheetConfigurator(),
                addFoodSheetConfigurator = AddFoodSheetConfigurator(dataProvider),
                foodConsumedDetailConfigurator = FoodConsumedDetailConfigurator(dataProvider, authProvider),
            )
        }
        val scope = rememberCoroutineScope()
        val mealTypes by viewModel.mealTypes.collectAsState()
        NavDisplay(
            backStack = viewModel.backStack,
            onBack = { viewModel.backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { destination ->
                when (destination) {
                    is DashboardDestination.Dashboard -> NavEntry(destination) {
                        DashboardView(viewModel = viewModel, router = router)
                    }
                    is DashboardDestination.FoodConsumedDetail -> NavEntry(destination) {
                        router.makeFoodConsumedDetailView(
                            food = destination.food,
                            mealTypes = mealTypes,
                            onBack = { viewModel.backStack.removeLastOrNull() },
                            onFoodUpdated = { scope.launch { viewModel.onFoodConsumedUpdated() } },
                        )
                    }
                }
            },
        )
    }
}
