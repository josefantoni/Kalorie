package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCase
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCase
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCase
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchMealTypesUseCase
import antoni.kalorie.core.usecases.FetchMyFoodItemReportUseCase
import antoni.kalorie.core.usecases.IsFavouriteFoodUseCase
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCase
import antoni.kalorie.core.usecases.SubmitFoodItemReportUseCase
import antoni.kalorie.core.usecases.UpdateFoodConsumedUseCase

class FoodConsumedDetailConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(
        food: FoodConsumedDomain,
        mealTypes: List<MealTypeDomain>,
        onBack: () -> Unit,
        onFoodUpdated: () -> Unit,
    ) {
        val viewModel = viewModel {
            FoodConsumedDetailViewModel(
                food = food,
                mealTypes = mealTypes,
                updateFoodConsumed = UpdateFoodConsumedUseCase(dataProvider, authProvider),
                assignFoodMealType = AssignFoodMealTypeUseCase(dataProvider, authProvider),
                fetchMealTypes = FetchMealTypesUseCase(dataProvider, authProvider),
                isFavouriteFood = IsFavouriteFoodUseCase(dataProvider, authProvider),
                addFavouriteFood = AddFavouriteFoodUseCase(dataProvider, authProvider),
                removeFavouriteFood = RemoveFavouriteFoodUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCase(),
                fetchMyFoodItemReport = FetchMyFoodItemReportUseCase(dataProvider, authProvider),
                submitFoodItemReport = SubmitFoodItemReportUseCase(dataProvider, authProvider),
                onFoodUpdated = onFoodUpdated,
            )
        }
        FoodConsumedDetailView(viewModel = viewModel, onBack = onBack)
    }
}
