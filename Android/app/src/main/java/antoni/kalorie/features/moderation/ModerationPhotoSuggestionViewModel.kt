package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionError
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.RejectPhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModerationPhotoSuggestionViewModel(
    val suggestion: PhotoSuggestionDomain,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val approvePhotoSuggestion: ApprovePhotoSuggestionUseCaseProtocol,
    private val rejectPhotoSuggestion: RejectPhotoSuggestionUseCaseProtocol,
    private val onResolved: (isApproved: Boolean) -> Unit,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _itemName = MutableStateFlow<String?>(null)
    val itemName: StateFlow<String?> = _itemName
    private val _canApprove = MutableStateFlow(true)
    val canApprove: StateFlow<Boolean> = _canApprove
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss
    val alertItem = MutableStateFlow<AlertItem?>(null)

    // MARK: - Functions

    suspend fun onAppear() {
        try {
            val item = fetchFoodItemByBarcode(suggestion.itemId)
            _itemName.value = item?.displayName
            if (item == null || item.photoUrl != null) {
                blockApproval(if (item == null) R.string.moderation_photoSuggestion_itemMissing else R.string.moderation_photoSuggestion_alreadyHasPhoto)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MODERATION)
        }
    }

    suspend fun onApproveTapped() {
        if (_state.value.isLoading) return
        _state.value = LoadingState.Loading
        try {
            approvePhotoSuggestion(suggestion)
            onResolved(true)
            _shouldDismiss.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApprovePhotoSuggestionError.ItemAlreadyHasPhoto) {
            blockApproval(R.string.moderation_photoSuggestion_alreadyHasPhoto)
        } catch (_: ApprovePhotoSuggestionError.ItemMissing) {
            blockApproval(R.string.moderation_photoSuggestion_itemMissing)
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onRejectTapped() {
        if (_state.value.isLoading) return
        _state.value = LoadingState.Loading
        try {
            rejectPhotoSuggestion(suggestion)
            onResolved(false)
            _shouldDismiss.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    // MARK: - Private

    private fun blockApproval(messageRes: Int) {
        _canApprove.value = false
        alertItem.value = AlertItem(titleRes = messageRes)
    }
}
