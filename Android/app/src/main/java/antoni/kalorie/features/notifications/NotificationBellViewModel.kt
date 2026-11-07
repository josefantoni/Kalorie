package antoni.kalorie.features.notifications

import androidx.lifecycle.ViewModel
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationBellViewModel(
    private val fetchMaintainerClaim: FetchMaintainerClaimUseCaseProtocol,
    private val fetchHasPendingNotifications: FetchHasPendingNotificationsUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _isBellVisible = MutableStateFlow(false)
    val isBellVisible: StateFlow<Boolean> = _isBellVisible
    private val _hasPendingNotifications = MutableStateFlow(false)
    val hasPendingNotifications: StateFlow<Boolean> = _hasPendingNotifications
    val isSheetVisible = MutableStateFlow(false)

    // MARK: - Functions

    suspend fun onAppear() {
        refresh()
    }

    suspend fun onForeground() {
        refresh()
    }

    fun onBellTapped() {
        isSheetVisible.value = true
    }

    suspend fun onSheetDismissed() {
        refreshDot()
    }

    // MARK: - Private

    private suspend fun refresh() {
        _isBellVisible.value = try {
            fetchMaintainerClaim()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MODERATION)
            false
        }
        refreshDot()
    }

    private suspend fun refreshDot() {
        if (!_isBellVisible.value) {
            _hasPendingNotifications.value = false
            return
        }
        _hasPendingNotifications.value = fetchHasPendingNotifications()
    }
}
