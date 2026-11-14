package antoni.kalorie.features.moderation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ReportRow(
    val report: FoodItemReportDomain,
    val itemName: String?,
) {
    val id: String get() = report.id
}

class ModerationReportsViewModel(
    private val fetchFoodItemReports: FetchFoodItemReportsUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _rows = MutableStateFlow<List<ReportRow>>(emptyList())
    val rows: StateFlow<List<ReportRow>> = _rows
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val backStack = mutableStateListOf<ModerationReportsDestination>(ModerationReportsDestination.Reports)

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

    fun onReportResolved(id: String) {
        _rows.value = _rows.value.filterNot { it.id == id }
    }

    // MARK: - Private

    private suspend fun load() {
        try {
            val reports = fetchFoodItemReports()
            val items = try {
                fetchFoodItemByBarcode(reports.map { it.barcode }.distinct())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.MODERATION)
                emptyList()
            }
            val namesByBarcode = buildMap { items.forEach { putIfAbsent(it.id, it.displayName) } }
            _rows.value = reports.map { ReportRow(it, namesByBarcode[it.barcode]) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        }
    }
}
