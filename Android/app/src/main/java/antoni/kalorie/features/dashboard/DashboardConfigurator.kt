package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.auth.MergeStatusReporting
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCase
import antoni.kalorie.core.usecases.CopyFoodsConsumedUseCase
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCase
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCase
import antoni.kalorie.core.usecases.FetchMealTypesUseCase
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCase
import antoni.kalorie.core.utils.ContextStringProvider
import antoni.kalorie.features.account.AccountConfigurator
import antoni.kalorie.features.addfoodsheet.AddFoodSheetConfigurator
import antoni.kalorie.features.settings.SettingsConfigurator
import kotlinx.coroutines.launch

class DashboardConfigurator {

    // MARK: - Functions

    @Composable
    fun createView(userId: String?, mergeStatusReporting: MergeStatusReporting) {
        val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
        val dataProvider = remember { FirestoreDataProvider() }
        val authProvider = remember { AuthProvider() }
        val context = LocalContext.current
        val stringProvider = remember { ContextStringProvider(context) }
        val signInSpotlightStore = remember { SignInSpotlightStore(context.applicationContext) }
        val viewModel = viewModel(key = "$userId/$languageTag") {
            DashboardViewModel(
                fetchMealTypes = FetchMealTypesUseCase(dataProvider, authProvider, stringProvider),
                fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCase(dataProvider, authProvider),
                setupDefaultMeals = SetupDefaultMealsUseCase(dataProvider, authProvider, stringProvider),
                confirmMealTypesEmpty = ConfirmMealTypesEmptyUseCase(dataProvider, authProvider),
                deleteFoodConsumed = DeleteFoodConsumedUseCase(dataProvider, authProvider),
                copyFoodsConsumed = CopyFoodsConsumedUseCase(dataProvider, authProvider),
                authProvider = authProvider,
                signInSpotlightStore = signInSpotlightStore,
            )
        }
        val router = remember(dataProvider, authProvider, mergeStatusReporting) {
            DashboardRouter(
                accountConfigurator = AccountConfigurator(dataProvider, authProvider, mergeStatusReporting),
                settingsConfigurator = SettingsConfigurator(dataProvider, authProvider),
                addFoodSheetConfigurator = AddFoodSheetConfigurator(dataProvider, authProvider),
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
