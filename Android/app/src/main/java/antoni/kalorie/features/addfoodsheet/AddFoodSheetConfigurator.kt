package antoni.kalorie.features.addfoodsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.KalorieApplication
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProvider
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCase
import antoni.kalorie.core.usecases.DeleteFoodPhotoUseCase
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCase
import antoni.kalorie.core.usecases.DeleteMySubmissionUseCase
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCase
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCase
import antoni.kalorie.core.usecases.FetchFoodFrequencyUseCase
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchFoodItemPersonalPortionsUseCase
import antoni.kalorie.core.usecases.FetchMealTypesUseCase
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCase
import antoni.kalorie.core.usecases.FetchMyFoodItemReportUseCase
import antoni.kalorie.core.usecases.FetchMySubmissionsUseCase
import antoni.kalorie.core.usecases.RecordFoodFrequencyUseCase
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCase
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCase
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCase
import antoni.kalorie.core.usecases.SaveFoodItemPersonalPortionsUseCase
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCase
import antoni.kalorie.core.usecases.SearchFoodItemsUseCase
import antoni.kalorie.core.usecases.SubmitFoodItemReportUseCase
import antoni.kalorie.core.usecases.SubmitFoodItemUseCase
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCase
import antoni.kalorie.core.usecases.UpdateMySubmissionUseCase
import antoni.kalorie.core.usecases.UploadFoodPhotoUseCase
import antoni.kalorie.core.utils.ContextStringProvider
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner
import antoni.kalorie.features.foodquantity.FoodQuantityUnit
import antoni.kalorie.features.foodquantity.FoodQuantityView
import antoni.kalorie.features.foodquantity.FoodQuantityViewModel
import antoni.kalorie.features.mycreatedmeal.MyCreatedMealEditorConfigurator
import java.time.Instant

class AddFoodSheetConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    private val editorConfigurator = MyCreatedMealEditorConfigurator(dataProvider, authProvider)

    @Composable
    fun createView(date: Instant, mealTypes: List<MealTypeDomain>, onDismiss: () -> Unit, onFoodSaved: () -> Unit = {}) {
        val context = LocalContext.current.applicationContext
        val stringProvider = remember { ContextStringProvider(context) }
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            val storageProvider = StorageDataProvider()
            val uploadFoodPhoto = UploadFoodPhotoUseCase(storageProvider)
            val deleteFoodPhoto = DeleteFoodPhotoUseCase(storageProvider)
            AddFoodSheetViewModel(
                searchFoodItems = SearchFoodItemsUseCase(dataProvider),
                submitFoodItem = SubmitFoodItemUseCase(dataProvider, authProvider, uploadFoodPhoto, deleteFoodPhoto),
                fetchMySubmissions = FetchMySubmissionsUseCase(dataProvider, authProvider),
                updateMySubmission = UpdateMySubmissionUseCase(dataProvider, authProvider, uploadFoodPhoto, deleteFoodPhoto),
                deleteMySubmission = DeleteMySubmissionUseCase(dataProvider, authProvider, deleteFoodPhoto),
                searchFoodExternally = SearchFoodExternallyUseCase(),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCase(),
                fetchFavouriteFoods = FetchFavouriteFoodsUseCase(dataProvider, authProvider),
                refreshFavouriteFood = RefreshFavouriteFoodUseCase(dataProvider, authProvider),
                fetchFoodFrequency = FetchFoodFrequencyUseCase(dataProvider, authProvider),
                fetchMyCreatedMeals = FetchMyCreatedMealsUseCase(dataProvider, authProvider),
                deleteMyCreatedMeal = DeleteMyCreatedMealUseCase(dataProvider, authProvider),
                onFoodSaved = onFoodSaved,
            )
        }
        AddFoodSheetView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeFoodQuantityView = { item, isFavourite, meal, onSaved, onFavouriteChanged, onMealUpdated, mealActions, onBack ->
                val quantityViewModel = viewModel {
                    FoodQuantityViewModel(
                        item = item,
                        saveFoodConsumed = SaveFoodConsumedUseCase(dataProvider, authProvider),
                        recordFoodFrequency = RecordFoodFrequencyUseCase(dataProvider, authProvider),
                        applicationScope = (context as KalorieApplication).applicationScope,
                        fetchMealTypes = FetchMealTypesUseCase(dataProvider, authProvider, stringProvider),
                        selectedDate = date,
                        mealTypes = mealTypes,
                        isFavourite = isFavourite,
                        addFavouriteFood = AddFavouriteFoodUseCase(dataProvider, authProvider),
                        removeFavouriteFood = RemoveFavouriteFoodUseCase(dataProvider, authProvider),
                        fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCase(dataProvider, authProvider),
                        saveFoodItemPersonalPortions = SaveFoodItemPersonalPortionsUseCase(dataProvider, authProvider),
                        fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                        fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCase(),
                        fetchMyFoodItemReport = FetchMyFoodItemReportUseCase(dataProvider, authProvider),
                        submitFoodItemReport = SubmitFoodItemReportUseCase(dataProvider, authProvider),
                        meal = meal,
                        updateMyCreatedMeal = UpdateMyCreatedMealUseCase(dataProvider, authProvider),
                        onSaved = onSaved,
                        onMealUpdated = onMealUpdated,
                        onFavouriteChanged = onFavouriteChanged,
                        quantity = if (meal != null) {
                            item.weight
                        } else if (item.portions.isEmpty()) {
                            100.0
                        } else {
                            1.0
                        },
                        unit = if (meal != null) FoodQuantityUnit.Grams else FoodQuantityViewModel.defaultUnit(item),
                    )
                }
                FoodQuantityView(viewModel = quantityViewModel, onBack = onBack, mealActions = mealActions)
            },
            makeMealEditorView = { onSaved, onDismiss, navigationIcon, header ->
                editorConfigurator.createView(
                    onSaved = onSaved,
                    dismissesOnSave = false,
                    onDismiss = onDismiss,
                    navigationIcon = navigationIcon,
                    header = header,
                )
            },
            makeEditMealView = { meal, onSaved, onDismiss, navigationIcon ->
                editorConfigurator.createView(
                    existingMeal = meal,
                    onSaved = onSaved,
                    onDismiss = onDismiss,
                    navigationIcon = navigationIcon,
                )
            },
        )
    }
}
