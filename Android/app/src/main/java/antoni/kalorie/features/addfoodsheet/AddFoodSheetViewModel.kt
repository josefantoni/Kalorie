package antoni.kalorie.features.addfoodsheet

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AddFoodSheetViewModel(
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
    private val searchFoodExternally: SearchFoodExternallyUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol,
    private val fetchFavouriteFoods: FetchFavouriteFoodsUseCaseProtocol,
    private val refreshFavouriteFood: RefreshFavouriteFoodUseCaseProtocol,
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
            val matchingIds = matchingFavourites.map { it.id }.toSet()
            return matchingFavourites + localFoodItems.value.filter { it.id !in matchingIds }
        }

    // MARK: - Functions

    fun onScannerButtonTapped() {
        isScannerVisible.value = true
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
        try {
            val items = fetchFavouriteFoods()
            favouriteFoods.value = items
            _favouriteIds.value = items.map { it.id }.toSet()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
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

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val EXTERNAL_SEARCH_MIN_LENGTH = 3

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
