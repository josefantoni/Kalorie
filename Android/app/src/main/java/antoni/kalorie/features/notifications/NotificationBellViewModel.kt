package antoni.kalorie.features.notifications

import androidx.lifecycle.ViewModel
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant

class NotificationBellViewModel(
    private val fetchMaintainerClaim: FetchMaintainerClaimUseCaseProtocol,
    private val fetchHasPendingNotifications: FetchHasPendingNotificationsUseCaseProtocol,
    private val fetchUserNotifications: FetchUserNotificationsUseCaseProtocol,
    private val notificationsStore: UserNotificationsStoreProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _isMaintainer = MutableStateFlow(false)
    val isMaintainer: StateFlow<Boolean> = _isMaintainer
    private val _hasPendingNotifications = MutableStateFlow(false)
    val hasPendingNotifications: StateFlow<Boolean> = _hasPendingNotifications
    val isSheetVisible = MutableStateFlow(false)
    private var refreshCount = 0
    private var needsDotRecompute = false
    private var isMaintainerResolved = false

    // MARK: - Functions

    suspend fun onAppear() {
        refresh()
    }

    suspend fun onForeground() {
        refresh()
    }

    suspend fun onRefresh() {
        refresh(ignoresGate = true)
    }

    suspend fun onDotRefreshNeeded() {
        if (!isMaintainerResolved || refreshCount > 0) return
        refreshDot(ignoresGate = false)
    }

    fun onBellTapped() {
        if (!isMaintainerResolved) return
        isSheetVisible.value = true
    }

    suspend fun onSheetDismissed() {
        if (!isMaintainerResolved) return
        if (refreshCount > 0) {
            needsDotRecompute = true
            return
        }
        refreshDot()
    }

    // MARK: - Private

    private suspend fun refresh(ignoresGate: Boolean = false) {
        if (refreshCount > 0 && !ignoresGate) return
        refreshCount++
        try {
            _isMaintainer.value = try {
                fetchMaintainerClaim()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.MODERATION)
                false
            }
            isMaintainerResolved = true
            refreshDot(ignoresGate)
            if (needsDotRecompute) {
                needsDotRecompute = false
                refreshDot(ignoresGate)
            }
        } finally {
            refreshCount--
        }
    }

    private suspend fun refreshDot(ignoresGate: Boolean = false) {
        if (_isMaintainer.value) {
            _hasPendingNotifications.value = fetchHasPendingNotifications()
            return
        }
        if (!ignoresGate && !isCheckDue()) {
            _hasPendingNotifications.value = false
            return
        }
        val startedAt = Instant.now()
        _hasPendingNotifications.value = try {
            val notifications = fetchUserNotifications()
            notificationsStore.recordFetch(notifications, startedAt)
            notificationsStore.mayHaveUnread
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MODERATION)
            false
        }
    }

    private fun isCheckDue(): Boolean {
        if (notificationsStore.mayHaveUnread) return true
        val lastCheckedAt = notificationsStore.lastCheckedAt ?: return true
        val elapsedSeconds = Duration.between(lastCheckedAt, Instant.now()).seconds
        return elapsedSeconds < 0 || elapsedSeconds > Constants.Notifications.USER_CHECK_INTERVAL_SECONDS
    }
}
