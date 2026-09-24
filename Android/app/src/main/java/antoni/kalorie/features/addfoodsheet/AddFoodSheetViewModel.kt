package antoni.kalorie.features.addfoodsheet

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AddFoodSheetViewModel(
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    val localFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val searchText = MutableStateFlow("")
    val isPushedToQuantityView = MutableStateFlow(false)
    private val _selectedFoodItem = MutableStateFlow<FoodItemDomain?>(null)
    val selectedFoodItem: StateFlow<FoodItemDomain?> = _selectedFoodItem
    @StringRes val searchExampleRes: Int = searchExamples.random()

    val displayedResults: List<FoodItemDomain>
        get() = localFoodItems.value

    // MARK: - Functions

    suspend fun onSearchTextChanged() {
        if (isPushedToQuantityView.value) return
        if (searchText.value.isEmpty()) {
            localFoodItems.value = emptyList()
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
    }

    fun onSelectFoodItem(item: FoodItemDomain) {
        if (isPushedToQuantityView.value) return
        _selectedFoodItem.value = item
        isPushedToQuantityView.value = true
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L

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
