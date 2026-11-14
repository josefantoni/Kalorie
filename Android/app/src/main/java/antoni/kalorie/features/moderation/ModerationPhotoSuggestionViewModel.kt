package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionError
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.RejectPhotoSuggestionUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModerationPhotoSuggestionViewModel(
    val suggestion: PhotoSuggestionDomain,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val approvePhotoSuggestion: ApprovePhotoSuggestionUseCaseProtocol,
    private val rejectPhotoSuggestion: RejectPhotoSuggestionUseCaseProtocol,
    private val responseTextProvider: ModerationResponseTextProviderProtocol,
    private val onResolved: () -> Unit,
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
    val responseDraft = MutableStateFlow<ModerationResponseDraft?>(null)
    private val _isSendingResponse = MutableStateFlow(false)
    val isSendingResponse: StateFlow<Boolean> = _isSendingResponse
    val alertItem = MutableStateFlow<AlertItem?>(null)

    // MARK: - Functions

    suspend fun onAppear() {
        try {
            val item = fetchFoodItemByBarcode(suggestion.itemId)
            _itemName.value = item?.displayName
            if (item == null) blockApproval(R.string.moderation_photoSuggestion_itemMissing)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MODERATION)
        }
    }

    fun onApproveTapped() {
        if (!_canApprove.value || _isSendingResponse.value) return
        responseDraft.value = ModerationResponseDraft(
            status = TicketStatus.ACCEPTED,
            text = responseTextProvider.photoAccepted(suggestion.locale),
        )
    }

    fun onRejectTapped() {
        if (_isSendingResponse.value) return
        responseDraft.value = ModerationResponseDraft(status = TicketStatus.REJECTED, text = "")
    }

    fun onResponseTextChanged(text: String) {
        responseDraft.value = responseDraft.value?.copy(text = text)
    }

    fun onResponseErrorDismissed() {
        responseDraft.value = responseDraft.value?.copy(errorTitleRes = null)
    }

    fun onResponseDismissed() {
        if (_isSendingResponse.value) return
        responseDraft.value = null
    }

    suspend fun onResponseSendTapped() {
        val draft = responseDraft.value ?: return
        if (!draft.isSendable || _isSendingResponse.value) return
        _isSendingResponse.value = true
        try {
            when (draft.status) {
                TicketStatus.ACCEPTED -> approvePhotoSuggestion(suggestion, draft.text)
                TicketStatus.REJECTED, TicketStatus.PENDING -> rejectPhotoSuggestion(suggestion, draft.text)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApprovePhotoSuggestionError.ItemMissing) {
            responseDraft.value = null
            blockApproval(R.string.moderation_photoSuggestion_itemMissing)
            return
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            responseDraft.value = responseDraft.value?.copy(errorTitleRes = R.string.common_error_unknown)
            return
        } finally {
            _isSendingResponse.value = false
        }
        responseDraft.value = null
        onResolved()
        _shouldDismiss.value = true
    }

    // MARK: - Private

    private fun blockApproval(messageRes: Int) {
        _canApprove.value = false
        alertItem.value = AlertItem(titleRes = messageRes)
    }
}
