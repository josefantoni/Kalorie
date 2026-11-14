package antoni.kalorie.features.notifications

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.HideUserNotificationUseCaseProtocol
import antoni.kalorie.core.usecases.MarkUserNotificationSeenUseCaseProtocol
import antoni.kalorie.core.usecases.WithdrawUserNotificationError
import antoni.kalorie.core.usecases.WithdrawUserNotificationUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.Instant

data class NotificationRow(
    val id: String,
    val kind: Kind,
    val title: String,
    val date: Instant,
) {
    sealed interface Kind {
        data class Submission(val submission: FoodItemSubmissionDomain) : Kind
        data class Report(val report: FoodItemReportDomain) : Kind
        data class PhotoSuggestion(val suggestion: PhotoSuggestionDomain) : Kind
        data class UserNotification(val notification: UserNotificationDomain) : Kind
    }

    val isHideable: Boolean
        get() = kind is Kind.UserNotification

    val isUnread: Boolean
        get() = (kind as? Kind.UserNotification)?.notification?.isUnread == true
}

enum class WithdrawResult { WITHDRAWN, ALREADY_RESOLVED, FAILED }

class NotificationsViewModel(
    private val fetchPendingSubmissions: FetchPendingSubmissionsUseCaseProtocol,
    private val fetchFoodItemReports: FetchFoodItemReportsUseCaseProtocol,
    private val fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val isMaintainer: Boolean,
    private val fetchUserNotifications: FetchUserNotificationsUseCaseProtocol,
    private val markUserNotificationSeen: MarkUserNotificationSeenUseCaseProtocol,
    private val hideUserNotification: HideUserNotificationUseCaseProtocol,
    private val withdrawUserNotification: WithdrawUserNotificationUseCaseProtocol,
    private val notificationsStore: UserNotificationsStoreProtocol,
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

    suspend fun onDetailShown(id: String) {
        val notification = (_rows.value.firstOrNull { it.id == id }?.kind as? NotificationRow.Kind.UserNotification)?.notification ?: return
        if (!notification.isUnread) return
        withContext(NonCancellable) {
            try {
                markUserNotificationSeen(notification)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.MODERATION)
                return@withContext
            }
            markSeenLocally(id)
        }
    }

    suspend fun onWithdrawConfirmed(id: String): WithdrawResult {
        val notification = (_rows.value.firstOrNull { it.id == id }?.kind as? NotificationRow.Kind.UserNotification)?.notification ?: return WithdrawResult.FAILED
        return withContext(NonCancellable) {
            try {
                withdrawUserNotification(notification)
            } catch (error: CancellationException) {
                throw error
            } catch (_: WithdrawUserNotificationError.AlreadyResolved) {
                load()
                return@withContext WithdrawResult.ALREADY_RESOLVED
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.MODERATION)
                return@withContext WithdrawResult.FAILED
            }
            _rows.value = _rows.value.filterNot { it.id == id }
            notificationsStore.mayHaveUnread = _rows.value.any { it.isUnread }
            WithdrawResult.WITHDRAWN
        }
    }

    suspend fun onHideTapped(id: String) {
        val index = _rows.value.indexOfFirst { it.id == id }
        val row = _rows.value.getOrNull(index) ?: return
        val notification = (row.kind as? NotificationRow.Kind.UserNotification)?.notification ?: return
        _rows.value = _rows.value.filterNot { it.id == id }
        withContext(NonCancellable) {
            try {
                hideUserNotification(notification)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.MODERATION)
                _rows.value = _rows.value.toMutableList().apply { add(minOf(index, size), row) }
                alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
                return@withContext
            }
            notificationsStore.mayHaveUnread = _rows.value.any { it.isUnread }
        }
    }

    // MARK: - Private

    private fun markSeenLocally(id: String) {
        _rows.value = _rows.value.map { row ->
            val kind = row.kind
            if (row.id == id && kind is NotificationRow.Kind.UserNotification) {
                row.copy(kind = kind.copy(notification = kind.notification.copy(seenAt = Instant.now())))
            } else {
                row
            }
        }
        if (_rows.value.none { it.isUnread }) {
            notificationsStore.mayHaveUnread = false
        }
    }

    private suspend fun load() {
        if (isMaintainer) loadModeration() else loadUserNotifications()
    }

    private suspend fun loadUserNotifications() {
        val startedAt = Instant.now()
        val notifications = try {
            fetchUserNotifications()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
            return
        }
        notificationsStore.recordFetch(notifications, startedAt)
        val items = try {
            fetchFoodItemByBarcode(notifications.map { it.itemId }.distinct())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        val namesById = buildMap { items.forEach { putIfAbsent(it.id, it.displayName) } }
        _rows.value = notifications.map {
            NotificationRow(it.rowId, NotificationRow.Kind.UserNotification(it), namesById[it.itemId] ?: it.itemId, it.lastEventAt)
        }
    }

    private suspend fun loadModeration() {
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
                is NotificationRow.Kind.UserNotification -> false
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
        val reportRows = reports.map {
            NotificationRow("report-${it.id}", NotificationRow.Kind.Report(it), namesById[it.barcode] ?: it.barcode, it.reportedAt)
        }
        val suggestionRows = suggestions.map {
            NotificationRow("photo-${it.id}", NotificationRow.Kind.PhotoSuggestion(it), namesById[it.itemId] ?: it.itemId, it.suggestedAt)
        }
        return (submissionRows + reportRows + suggestionRows).sortedBy { it.date }
    }
}
