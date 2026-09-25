package antoni.kalorie.features.addfoodsheet

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCaseProtocol
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AddFoodSheetMode(@StringRes val titleRes: Int) {
    SEARCH(R.string.addFood_mode_search),
    CREATE_MEAL(R.string.addFood_mode_createMeal),
}

class AddFoodSheetViewModel(
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
    private val searchFoodExternally: SearchFoodExternallyUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol,
    private val fetchFavouriteFoods: FetchFavouriteFoodsUseCaseProtocol,
    private val refreshFavouriteFood: RefreshFavouriteFoodUseCaseProtocol,
    private val fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseProtocol,
    private val deleteMyCreatedMeal: DeleteMyCreatedMealUseCaseProtocol,
    private val onFoodSaved: () -> Unit = {},
    isScannerVisible: Boolean = false,
) : ViewModel() {

    // MARK: - Properties

    val localFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val externalFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    private val _isExternalSearchLoading = MutableStateFlow(false)
    val isExternalSearchLoading: StateFlow<Boolean> = _isExternalSearchLoading
    val favouriteFoods = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    private val _favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds
    val searchText = MutableStateFlow("")
    private val _mode = MutableStateFlow(AddFoodSheetMode.SEARCH)
    val mode: StateFlow<AddFoodSheetMode> = _mode
    val myCreatedMeals = MutableStateFlow<List<MyCreatedMealDomain>>(emptyList())
    val isMealDeleteConfirmationVisible = MutableStateFlow(false)
    private var mealPendingDeletion: MyCreatedMealDomain? = null
    val isScannerVisible = MutableStateFlow(isScannerVisible)
    val lastScannedBarcode = MutableStateFlow("")
    private val _isBarcodeSearchLoading = MutableStateFlow(false)
    val isBarcodeSearchLoading: StateFlow<Boolean> = _isBarcodeSearchLoading
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val isPushedToQuantityView = MutableStateFlow(false)
    private val _selectedFoodItem = MutableStateFlow<FoodItemDomain?>(null)
    val selectedFoodItem: StateFlow<FoodItemDomain?> = _selectedFoodItem
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss
    @StringRes val searchExampleRes: Int = searchExamples.random()

    val displayedResults: List<FoodItemDomain>
        get() {
            val query = searchText.value.lowercase()
            if (query.isEmpty()) return localFoodItems.value
            val matchingFavourites = favouriteFoods.value.filter {
                it.czName.lowercase().startsWith(query) || it.engName.lowercase().startsWith(query)
            }
            val favouriteIds = matchingFavourites.map { it.id }.toSet()
            val matchingMeals = myCreatedMeals.value
                .map { it.asFoodItem() }
                .filter { it.czName.lowercase().startsWith(query) && it.id !in favouriteIds }
            val matchingIds = favouriteIds + matchingMeals.map { it.id }
            return matchingFavourites + matchingMeals + localFoodItems.value.filter { it.id !in matchingIds }
        }

    // MARK: - Functions

    fun onScannerButtonTapped() {
        _mode.value = AddFoodSheetMode.SEARCH
        isScannerVisible.value = true
    }

    fun onModeSelected(mode: AddFoodSheetMode) {
        if (mode == _mode.value) return
        _mode.value = mode
        isScannerVisible.value = false
    }

    fun onDeleteMealRequested(meal: MyCreatedMealDomain) {
        mealPendingDeletion = meal
        isMealDeleteConfirmationVisible.value = true
    }

    suspend fun onDeleteMealConfirmed() {
        val meal = mealPendingDeletion ?: return
        mealPendingDeletion = null
        onDeleteMealConfirmed(meal)
    }

    suspend fun onDeleteMealConfirmed(meal: MyCreatedMealDomain) {
        isPushedToQuantityView.value = false
        val index = myCreatedMeals.value.indexOfFirst { it.id == meal.id }
        myCreatedMeals.value = myCreatedMeals.value.filter { it.id != meal.id }
        try {
            deleteMyCreatedMeal(meal.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ADD_FOOD_SHEET)
            if (index >= 0) {
                myCreatedMeals.value = myCreatedMeals.value.toMutableList().also { it.add(minOf(index, it.size), meal) }
            }
            alertItem.value = AlertItem(titleRes = R.string.myCreatedMeal_error_deleteFailed)
        }
    }

    suspend fun onMyCreatedMealSaved() {
        _mode.value = AddFoodSheetMode.SEARCH
        isPushedToQuantityView.value = false
        try {
            myCreatedMeals.value = fetchMyCreatedMeals()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
        }
    }

    fun isMyCreatedMeal(item: FoodItemDomain): Boolean = item.kind == FoodItemKind.CREATED_MEAL

    fun myCreatedMeal(item: FoodItemDomain): MyCreatedMealDomain? {
        if (!isMyCreatedMeal(item)) return null
        return myCreatedMeals.value.firstOrNull { it.id == item.id }
    }

    fun onMyCreatedMealUpdated(meal: MyCreatedMealDomain) {
        val index = myCreatedMeals.value.indexOfFirst { it.id == meal.id }
        if (index < 0) return
        myCreatedMeals.value = myCreatedMeals.value.toMutableList().also { it[index] = meal }
    }

    fun onScenePhaseActive(isCameraAvailable: Boolean) {
        if (!isScannerVisible.value || isCameraAvailable) return
        isScannerVisible.value = false
        alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert)
    }

    suspend fun onBarcodeScanned() {
        val barcode = lastScannedBarcode.value
        if (barcode.isEmpty()) return
        _isBarcodeSearchLoading.value = true
        try {
            try {
                fetchFoodItemByBarcode(barcode)?.let { local ->
                    isScannerVisible.value = false
                    onSelectFoodItem(local)
                    return
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
            }
            try {
                fetchFoodByBarcodeExternally(barcode)?.let { external ->
                    isScannerVisible.value = false
                    onSelectFoodItem(external)
                    return
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.ADD_FOOD_SHEET)
                alertItem.value = AlertItem(titleRes = R.string.addFood_error_loadFailed)
                return
            }
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_barcodeNotFound)
        } finally {
            lastScannedBarcode.value = ""
            _isBarcodeSearchLoading.value = false
        }
    }

    suspend fun onSearchTextChanged() {
        if (isPushedToQuantityView.value) return
        if (searchText.value.isEmpty()) {
            localFoodItems.value = emptyList()
            externalFoodItems.value = emptyList()
            return
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        try {
            localFoodItems.value = searchFoodItems(searchText.value)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            localFoodItems.value = emptyList()
            externalFoodItems.value = emptyList()
            return
        }
        if (displayedResults.isNotEmpty() || searchText.value.length < EXTERNAL_SEARCH_MIN_LENGTH) {
            externalFoodItems.value = emptyList()
            return
        }
        _isExternalSearchLoading.value = true
        try {
            externalFoodItems.value = searchFoodExternally(searchText.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
            externalFoodItems.value = emptyList()
        } finally {
            _isExternalSearchLoading.value = false
        }
    }

    fun onSelectFoodItem(item: FoodItemDomain) {
        if (isPushedToQuantityView.value) return
        _selectedFoodItem.value = item
        isPushedToQuantityView.value = true
    }

    suspend fun onSelectFavouriteFood(item: FoodItemDomain) {
        if (isPushedToQuantityView.value) return
        var resolved = item
        try {
            resolved = refreshFavouriteFood(item)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
        }
        val index = favouriteFoods.value.indexOfFirst { it.id == item.id }
        if (resolved != item && index >= 0) {
            favouriteFoods.value = favouriteFoods.value.toMutableList().also { it[index] = resolved }
        }
        onSelectFoodItem(resolved)
    }

    suspend fun onAppear() {
        coroutineScope {            val favourites = async {
                try {
                    fetchFavouriteFoods()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
                    null
                }
            }
            val meals = async {
                try {
                    fetchMyCreatedMeals()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
                    null
                }
            }
            favourites.await()?.let { items ->
                favouriteFoods.value = items
                _favouriteIds.value = items.map { it.id }.toSet()
            }
            meals.await()?.let { myCreatedMeals.value = it }
        }
    }

    fun isFavourite(item: FoodItemDomain): Boolean = item.id in _favouriteIds.value

    fun onFavouriteChanged(id: String, isFavourite: Boolean, item: FoodItemDomain) {
        favouriteFoods.value = favouriteFoods.value.filter { it.id != id }
        if (isFavourite) {
            _favouriteIds.value = _favouriteIds.value + id
            favouriteFoods.value = listOf(item) + favouriteFoods.value
        } else {
            _favouriteIds.value = _favouriteIds.value - id
        }
    }

    fun onFoodConsumedSaved() {
        onFoodSaved()
        _shouldDismiss.value = true
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
        private const val EXTERNAL_SEARCH_MIN_LENGTH = 3

        val searchExamples = listOf(
            R.string.addFood_search_example_01,
            R.string.addFood_search_example_02,
            R.string.addFood_search_example_03,
            R.string.addFood_search_example_04,
            R.string.addFood_search_example_05,
            R.string.addFood_search_example_06,
            R.string.addFood_search_example_07,
            R.string.addFood_search_example_08,
        )
    }
}
