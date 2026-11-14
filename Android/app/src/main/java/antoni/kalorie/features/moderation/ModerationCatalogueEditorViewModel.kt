package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.ResolveFoodItemReportUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodItemError
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.NutritionLabelPrefilling
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.addfoodsheet.FoodItemFormInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ModerationCatalogueEditorViewModel(
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val updateFoodItem: UpdateFoodItemUseCaseProtocol,
    private val resolveFoodItemReport: ResolveFoodItemReportUseCaseProtocol,
    private val responseTextProvider: ModerationResponseTextProviderProtocol,
    val ticket: FoodItemReportDomain? = null,
    private val applicationScope: CoroutineScope,
    private val onResolved: () -> Unit = {},
) : ViewModel(),
    NutritionLabelPrefilling {

    // MARK: - Properties

    val isOpenedFromReport: Boolean = ticket != null
    val barcodeQuery = MutableStateFlow(ticket?.barcode ?: "")
    private val _loadedItem = MutableStateFlow<FoodItemDomain?>(null)
    val loadedItem: StateFlow<FoodItemDomain?> = _loadedItem
    override val formInput = MutableStateFlow(FoodItemFormInput())
    override val recognizedFields = MutableStateFlow<Set<FoodItemFormField>>(emptySet())
    override val isNutritionLabelCameraVisible = MutableStateFlow(false)
    override val nutritionLabelCameraHintRes = MutableStateFlow<Int?>(null)
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    val alertItem = MutableStateFlow<AlertItem?>(null)
    private val _showCheckmark = MutableStateFlow(false)
    val showCheckmark: StateFlow<Boolean> = _showCheckmark
    val responseDraft = MutableStateFlow<ModerationResponseDraft?>(null)
    private val _isSendingResponse = MutableStateFlow(false)
    val isSendingResponse: StateFlow<Boolean> = _isSendingResponse
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss

    // MARK: - Functions

    suspend fun onAppear() {
        val barcode = ticket?.barcode ?: return
        if (_loadedItem.value != null) return
        barcodeQuery.value = barcode
        onSearchTapped()
    }

    fun onNutritionLabelRecognized(reading: NutritionLabelReading, liveBarcode: String?) {
        applyRecognizedNutritionLabel(reading, liveBarcode)
    }

    fun onNutritionLabelCameraTapped() {
        isNutritionLabelCameraVisible.value = true
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
            recognizedFields.value = emptySet()
            _showCheckmark.value = false
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
        if (_state.value.isLoading) return
        if (!save()) return
        _showCheckmark.value = true
        try {
            delay(CHECKMARK_DURATION_MILLIS)
        } finally {
            _showCheckmark.value = false
        }
    }

    suspend fun onSaveAndResolveTapped() {
        val ticket = ticket ?: return
        if (_state.value.isLoading) return
        var isSaved = false
        runInApplicationScope { isSaved = save() }
        if (!isSaved) return
        responseDraft.value = ModerationResponseDraft(
            status = TicketStatus.ACCEPTED,
            text = responseTextProvider.reportAccepted(ticket.locale),
        )
    }

    fun onRejectTapped() {
        if (ticket == null || _state.value.isLoading) return
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
        val ticket = ticket ?: return
        val draft = responseDraft.value ?: return
        if (!draft.isSendable || _isSendingResponse.value) return
        _isSendingResponse.value = true
        var isResolved = false
        try {
            runInApplicationScope {
                try {
                    resolveFoodItemReport(ticket.id, draft.status, draft.text)
                    isResolved = true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.error(error, Constants.LogCategory.MODERATION)
                }
            }
        } finally {
            _isSendingResponse.value = false
        }
        if (!isResolved) {
            responseDraft.value = responseDraft.value?.copy(errorTitleRes = R.string.common_error_unknown)
            return
        }
        responseDraft.value = null
        onResolved()
        _shouldDismiss.value = true
    }

    // MARK: - Private

    private suspend fun save(): Boolean {
        val loadedItem = _loadedItem.value ?: return false
        _state.value = LoadingState.Loading
        val item = formInput.value.asFoodItemDomain(id = loadedItem.id, date = loadedItem.date)
        return try {
            val savedItem = updateFoodItem(item, loadedItem, formInput.value.photo)
            _loadedItem.value = savedItem
            formInput.value = formInput.value.copy(
                photo = savedItem.photoUrl?.let { FoodItemFormPhoto.Remote(it) } ?: FoodItemFormPhoto.None,
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(
                titleRes = when (error) {
                    is UpdateFoodItemError.InvalidCode -> R.string.addFood_error_invalidCode
                    is UpdateFoodItemError.InvalidName -> R.string.addFood_error_invalidName
                    is UpdateFoodItemError.InvalidCalories -> R.string.addFood_error_invalidCalories
                    is UpdateFoodItemError.InvalidAlcoholByVolume -> R.string.addFood_error_invalidAlcoholByVolume
                    is UpdateFoodItemError.InvalidPortion -> error.error.alertTitleRes
                    is UpdateFoodItemError.ChangedSinceLoad -> R.string.moderation_error_itemChangedSinceLoad
                    else -> R.string.common_error_unknown
                },
            )
            false
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    private suspend fun runInApplicationScope(block: suspend () -> Unit) {
        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) { block() }.join()
    }

    private companion object {
        const val CHECKMARK_DURATION_MILLIS = 2_000L
    }
}
