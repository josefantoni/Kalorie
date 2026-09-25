package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModerationQueueViewModel(
    private val fetchPendingSubmissions: FetchPendingSubmissionsUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _submissions = MutableStateFlow<List<FoodItemSubmissionDomain>>(emptyList())
    val submissions: StateFlow<List<FoodItemSubmissionDomain>> = _submissions
    private val _collidingBarcodes = MutableStateFlow<Set<String>>(emptySet())
    val collidingBarcodes: StateFlow<Set<String>> = _collidingBarcodes
    val alertItem = MutableStateFlow<AlertItem?>(null)

    // MARK: - Functions

    suspend fun onAppear() {
        _state.value = LoadingState.Loading
        try {
            _submissions.value = fetchPendingSubmissions()
            refreshCollisions()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onRefresh() {
        onAppear()
    }

    suspend fun onSubmissionResolved(id: String) {
        _submissions.value = _submissions.value.filterNot { it.id == id }
        refreshCollisions()
    }

    fun isColliding(submission: FoodItemSubmissionDomain): Boolean {
        val barcode = submission.barcode ?: return false
        return barcode in _collidingBarcodes.value
    }

    // MARK: - Private

    private suspend fun refreshCollisions() {
        val barcodes = _submissions.value.mapNotNull { it.barcode }
        val existing = try {
            fetchFoodItemByBarcode(barcodes)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        _collidingBarcodes.value = existing.map { it.id }.toSet()
    }
}
