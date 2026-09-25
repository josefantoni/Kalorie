package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.DeleteFoodItemReportUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ReportGroup(
    val barcode: String,
    val itemName: String?,
    val reports: List<FoodItemReportDomain>,
)

class ModerationReportsViewModel(
    private val fetchFoodItemReports: FetchFoodItemReportsUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val deleteFoodItemReport: DeleteFoodItemReportUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _groups = MutableStateFlow<List<ReportGroup>>(emptyList())
    val groups: StateFlow<List<ReportGroup>> = _groups
    val alertItem = MutableStateFlow<AlertItem?>(null)

    // MARK: - Functions

    suspend fun onAppear() {
        _state.value = LoadingState.Loading
        try {
            val reports = fetchFoodItemReports()
            _groups.value = grouped(reports)
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

    suspend fun onResolveTapped(group: ReportGroup) {
        val remainingReports = mutableListOf<FoodItemReportDomain>()
        for (report in group.reports) {
            try {
                deleteFoodItemReport(report.barcode, report.reportedBy)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.MODERATION)
                remainingReports += report
            }
        }
        if (remainingReports.isEmpty()) {
            _groups.value = _groups.value.filterNot { it.barcode == group.barcode }
            return
        }
        _groups.value = _groups.value.map { existing ->
            if (existing.barcode == group.barcode) existing.copy(reports = remainingReports) else existing
        }
        alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
    }

    // MARK: - Private

    private suspend fun grouped(reports: List<FoodItemReportDomain>): List<ReportGroup> {
        val barcodes = reports.map { it.barcode }.distinct()
        val items = try {
            fetchFoodItemByBarcode(barcodes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MODERATION)
            emptyList()
        }
        val namesByBarcode = buildMap { items.forEach { putIfAbsent(it.id, it.displayName) } }
        return reports.groupBy { it.barcode }
            .map { (barcode, barcodeReports) ->
                ReportGroup(
                    barcode = barcode,
                    itemName = namesByBarcode[barcode],
                    reports = barcodeReports.sortedByDescending { it.reportedAt },
                )
            }
            .sortedWith(
                compareByDescending<ReportGroup> { it.reports.size }
                    .thenByDescending { it.reports.firstOrNull()?.reportedAt ?: Instant.MIN },
            )
    }
}
