package antoni.kalorie.features.notifications

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

data class NotificationRow(
    val id: String,
    val kind: Kind,
    val title: String,
    val date: Instant,
) {
    sealed interface Kind {
        data class Submission(val submission: FoodItemSubmissionDomain) : Kind
        data class Report(val barcode: String, val reports: List<FoodItemReportDomain>) : Kind
        data class PhotoSuggestion(val suggestion: PhotoSuggestionDomain) : Kind
    }
}

class NotificationsViewModel(
    private val fetchPendingSubmissions: FetchPendingSubmissionsUseCaseProtocol,
    private val fetchFoodItemReports: FetchFoodItemReportsUseCaseProtocol,
    private val fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _rows = MutableStateFlow<List<NotificationRow>>(emptyList())
    val rows: StateFlow<List<NotificationRow>> = _rows
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val backStack = mutableStateListOf<NotificationsDestination>(NotificationsDestination.List)

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

    fun onRowResolved(id: String) {
        _rows.value = _rows.value.filterNot { it.id == id }
    }

    fun onPhotoSuggestionResolved(rowId: String, isApproved: Boolean) {
        val itemId = (_rows.value.firstOrNull { it.id == rowId }?.kind as? NotificationRow.Kind.PhotoSuggestion)?.suggestion?.itemId
        _rows.value = _rows.value.filterNot { row ->
            val suggestion = (row.kind as? NotificationRow.Kind.PhotoSuggestion)?.suggestion
            row.id == rowId || (isApproved && suggestion != null && suggestion.itemId == itemId)
        }
    }

    fun onReportRowChanged(id: String, remaining: List<FoodItemReportDomain>) {
        _rows.value = _rows.value.mapNotNull { row ->
            val kind = row.kind
            when {
                row.id != id -> row
                remaining.isEmpty() || kind !is NotificationRow.Kind.Report -> null
                else -> row.copy(kind = kind.copy(reports = remaining))
            }
        }
    }

    // MARK: - Private

    private suspend fun load() {
        val (fetchedSubmissions, fetchedReports, fetchedSuggestions) = coroutineScope {
            val submissions = async { attempt { fetchPendingSubmissions() } }
            val reports = async { attempt { fetchFoodItemReports() } }
            val suggestions = async { attempt { fetchPhotoSuggestions() } }
            Triple(submissions.await(), reports.await(), suggestions.await())
        }
        if (fetchedSubmissions == null || fetchedReports == null || fetchedSuggestions == null) {
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        }
        val submissions = fetchedSubmissions.orEmpty()
        val reports = fetchedReports.orEmpty()
        val suggestions = fetchedSuggestions.orEmpty()

        val barcodes = (reports.map { it.barcode } + suggestions.map { it.itemId }).distinct()
        val items = try {
            fetchFoodItemByBarcode(barcodes)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        val namesById = buildMap { items.forEach { putIfAbsent(it.id, it.displayName) } }
        val keptRows = _rows.value.filter { row ->
            when (row.kind) {
                is NotificationRow.Kind.Submission -> fetchedSubmissions == null
                is NotificationRow.Kind.Report -> fetchedReports == null
                is NotificationRow.Kind.PhotoSuggestion -> fetchedSuggestions == null
            }
        }
        _rows.value = (rows(submissions, reports, suggestions, namesById) + keptRows).sortedBy { it.date }
    }

    private suspend fun <T> attempt(operation: suspend () -> T): T? = try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.error(error, Constants.LogCategory.MODERATION)
        null
    }

    private fun rows(
        submissions: List<FoodItemSubmissionDomain>,
        reports: List<FoodItemReportDomain>,
        suggestions: List<PhotoSuggestionDomain>,
        namesById: Map<String, String>,
    ): List<NotificationRow> {
        val submissionRows = submissions.map {
            NotificationRow("submission-${it.id}", NotificationRow.Kind.Submission(it), it.item.displayName, it.submittedAt)
        }
        val reportRows = reports.groupBy { it.barcode }.map { (barcode, barcodeReports) ->
            NotificationRow(
                id = "report-$barcode",
                kind = NotificationRow.Kind.Report(barcode, barcodeReports.sortedByDescending { it.reportedAt }),
                title = namesById[barcode] ?: barcode,
                date = barcodeReports.minOf { it.reportedAt },
            )
        }
        val suggestionRows = suggestions.map {
            NotificationRow("photo-${it.id}", NotificationRow.Kind.PhotoSuggestion(it), namesById[it.itemId] ?: it.itemId, it.suggestedAt)
        }
        return (submissionRows + reportRows + suggestionRows).sortedBy { it.date }
    }
}
