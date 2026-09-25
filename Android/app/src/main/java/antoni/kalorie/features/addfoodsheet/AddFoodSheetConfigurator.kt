package antoni.kalorie.features.addfoodsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCase
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCase
import antoni.kalorie.core.usecases.FetchFoodItemPersonalPortionsUseCase
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCase
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchMealTypesUseCase
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCase
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCase
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCase
import antoni.kalorie.core.usecases.SaveFoodItemPersonalPortionsUseCase
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCase
import antoni.kalorie.core.usecases.SearchFoodItemsUseCase
import antoni.kalorie.features.foodquantity.FoodQuantityView
import antoni.kalorie.features.foodquantity.FoodQuantityViewModel
import java.time.Instant
import java.util.UUID

class AddFoodSheetConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(date: Instant, mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onFoodSaved: () -> Unit = {}) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            AddFoodSheetViewModel(
                searchFoodItems = SearchFoodItemsUseCase(dataProvider),
                searchFoodExternally = SearchFoodExternallyUseCase(),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCase(),
                fetchFavouriteFoods = FetchFavouriteFoodsUseCase(dataProvider, authProvider),
                refreshFavouriteFood = RefreshFavouriteFoodUseCase(dataProvider, authProvider),
                onFoodSaved = onFoodSaved,
            )
        }
        AddFoodSheetView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeFoodQuantityView = { item, isFavourite, onSaved, onFavouriteChanged, onBack ->
                val quantityViewModel = viewModel {
                    FoodQuantityViewModel(
                        item = item,
                        saveFoodConsumed = SaveFoodConsumedUseCase(dataProvider, authProvider),
                        fetchMealTypes = FetchMealTypesUseCase(dataProvider, authProvider),
                        selectedDate = date,
                        mealTypes = mealTypes,
                        isFavourite = isFavourite,
                        addFavouriteFood = AddFavouriteFoodUseCase(dataProvider, authProvider),
                        removeFavouriteFood = RemoveFavouriteFoodUseCase(dataProvider, authProvider),
                        fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCase(dataProvider, authProvider),
                        saveFoodItemPersonalPortions = SaveFoodItemPersonalPortionsUseCase(dataProvider, authProvider),
                        onSaved = onSaved,
                        onFavouriteChanged = onFavouriteChanged,
                        quantity = if (item.portions.isEmpty()) 100.0 else 1.0,
                        unit = FoodQuantityViewModel.defaultUnit(item),
                    )
                }
                FoodQuantityView(viewModel = quantityViewModel, onBack = onBack)
            },
        )
    }
}
