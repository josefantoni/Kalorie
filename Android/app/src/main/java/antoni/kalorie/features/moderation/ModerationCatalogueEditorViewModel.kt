package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodItemError
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.features.addfoodsheet.FoodItemFormInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModerationCatalogueEditorViewModel(
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val updateFoodItem: UpdateFoodItemUseCaseProtocol,
    private val initialBarcode: String? = null,
) : ViewModel() {

    // MARK: - Properties

    val barcodeQuery = MutableStateFlow(initialBarcode ?: "")
    private val _loadedItem = MutableStateFlow<FoodItemDomain?>(null)
    val loadedItem: StateFlow<FoodItemDomain?> = _loadedItem
    val formInput = MutableStateFlow(FoodItemFormInput())
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    val alertItem = MutableStateFlow<AlertItem?>(null)
    private val _didSave = MutableStateFlow(false)
    val didSave: StateFlow<Boolean> = _didSave

    // MARK: - Functions

    suspend fun onAppear() {
        val barcode = initialBarcode ?: return
        if (_loadedItem.value != null) return
        barcodeQuery.value = barcode
        onSearchTapped()
    }

    suspend fun onSearchTapped() {
        val query = barcodeQuery.value
        if (query.isEmpty()) return
        _state.value = LoadingState.Loading
        try {
            val item = fetchFoodItemByBarcode(query)
            if (item == null) {
                _loadedItem.value = null
                alertItem.value = AlertItem(titleRes = R.string.addFood_error_barcodeNotFound)
                return
            }
            _loadedItem.value = item
            formInput.value = FoodItemFormInput.from(item)
            _didSave.value = false
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onSaveTapped() {
        val loadedItem = _loadedItem.value ?: return
        _state.value = LoadingState.Loading
        val item = formInput.value.asFoodItemDomain(date = loadedItem.date)
        try {
            updateFoodItem(item, loadedItem)
            _loadedItem.value = item
            _didSave.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(
                titleRes = when (error) {
                    is UpdateFoodItemError.InvalidCode -> R.string.addFood_error_invalidCode
                    is UpdateFoodItemError.InvalidName -> R.string.addFood_error_invalidName
                    is UpdateFoodItemError.InvalidCalories -> R.string.addFood_error_invalidCalories
                    is UpdateFoodItemError.InvalidWeight -> R.string.addFood_error_invalidWeight
                    is UpdateFoodItemError.InvalidPortion -> error.error.alertTitleRes
                    is UpdateFoodItemError.ChangedSinceLoad -> R.string.moderation_error_itemChangedSinceLoad
                    else -> R.string.common_error_unknown
                },
            )
        } finally {
            _state.value = LoadingState.loaded
        }
    }
}
