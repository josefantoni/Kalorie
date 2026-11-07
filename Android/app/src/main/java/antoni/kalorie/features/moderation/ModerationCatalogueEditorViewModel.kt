package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.usecases.DeleteFoodItemReportUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
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
    private val deleteFoodItemReport: DeleteFoodItemReportUseCaseProtocol,
    private val initialBarcode: String? = null,
    reports: List<FoodItemReportDomain> = emptyList(),
    private val applicationScope: CoroutineScope,
    private val onReportsChanged: (List<FoodItemReportDomain>) -> Unit = {},
) : ViewModel(),
    NutritionLabelPrefilling {

    // MARK: - Properties

    val isOpenedFromReport: Boolean = initialBarcode != null
    val barcodeQuery = MutableStateFlow(initialBarcode ?: "")
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
    private val _reports = MutableStateFlow(reports)
    val reports: StateFlow<List<FoodItemReportDomain>> = _reports
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss

    // MARK: - Functions

    suspend fun onAppear() {
        val barcode = initialBarcode ?: return
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
        if (_state.value.isLoading) return
        runInApplicationScope {
            if (!save()) return@runInApplicationScope
            resolveReports()
        }
    }

    suspend fun onRejectTapped() {
        if (_state.value.isLoading) return
        runInApplicationScope { resolveReports() }
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

    private suspend fun resolveReports() {
        _state.value = LoadingState.Loading
        try {
            val remainingReports = mutableListOf<FoodItemReportDomain>()
            for (report in _reports.value) {
                try {
                    deleteFoodItemReport(report.barcode, report.reportedBy)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.error(error, Constants.LogCategory.MODERATION)
                    remainingReports += report
                }
            }
            _reports.value = remainingReports
            onReportsChanged(remainingReports)
            if (remainingReports.isNotEmpty()) {
                alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
                return
            }
            _shouldDismiss.value = true
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    private companion object {
        const val CHECKMARK_DURATION_MILLIS = 2_000L
    }
}
