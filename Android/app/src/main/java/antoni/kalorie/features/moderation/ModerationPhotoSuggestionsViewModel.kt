package antoni.kalorie.features.moderation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PhotoSuggestionRow(
    val suggestion: PhotoSuggestionDomain,
    val itemName: String?,
) {
    val id: String get() = suggestion.id
}

class ModerationPhotoSuggestionsViewModel(
    private val fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _rows = MutableStateFlow<List<PhotoSuggestionRow>>(emptyList())
    val rows: StateFlow<List<PhotoSuggestionRow>> = _rows
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val backStack = mutableStateListOf<ModerationPhotoSuggestionsDestination>(ModerationPhotoSuggestionsDestination.Suggestions)

    // MARK: - Functions

    suspend fun onAppear() {
        _state.value = LoadingState.Loading
        try {
            load()
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onRefresh() {
        load()
    }

    fun onSuggestionResolved(id: String, isApproved: Boolean) {
        val itemId = _rows.value.firstOrNull { it.id == id }?.suggestion?.itemId
        _rows.value = _rows.value.filterNot { it.id == id || (isApproved && it.suggestion.itemId == itemId) }
    }

    // MARK: - Private

    private suspend fun load() {
        try {
            val suggestions = fetchPhotoSuggestions()
            val items = try {
                fetchFoodItemByBarcode(suggestions.map { it.itemId }.distinct())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            val namesById = buildMap { items.forEach { putIfAbsent(it.id, it.displayName) } }
            _rows.value = suggestions.sortedBy { it.suggestedAt }.map { PhotoSuggestionRow(it, namesById[it.itemId]) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        }
    }
}
