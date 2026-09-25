package antoni.kalorie.features.addfoodsheet

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AddFoodSheetViewModel(
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
    private val searchFoodExternally: SearchFoodExternallyUseCaseProtocol,
    private val onFoodSaved: () -> Unit = {},
) : ViewModel() {

    // MARK: - Properties

    val localFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val externalFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    private val _isExternalSearchLoading = MutableStateFlow(false)
    val isExternalSearchLoading: StateFlow<Boolean> = _isExternalSearchLoading
    val searchText = MutableStateFlow("")
    val isPushedToQuantityView = MutableStateFlow(false)
    private val _selectedFoodItem = MutableStateFlow<FoodItemDomain?>(null)
    val selectedFoodItem: StateFlow<FoodItemDomain?> = _selectedFoodItem
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss
    @StringRes val searchExampleRes: Int = searchExamples.random()

    val displayedResults: List<FoodItemDomain>
        get() = localFoodItems.value

    // MARK: - Functions

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
