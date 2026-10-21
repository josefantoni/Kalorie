package antoni.kalorie.application

import androidx.lifecycle.ViewModel
import antoni.kalorie.core.models.AppVersion
import antoni.kalorie.core.usecases.FetchMinimumVersionUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppVersionGate(
    private val fetchMinimumVersion: FetchMinimumVersionUseCaseProtocol,
    private val currentVersion: AppVersion?,
) : ViewModel() {

    // MARK: - Properties

    private val _isUpdateRequired = MutableStateFlow(false)
    val isUpdateRequired: StateFlow<Boolean> = _isUpdateRequired

    // MARK: - Functions

    suspend fun check() {
        val current = currentVersion ?: return
        try {
            val minimum = fetchMinimumVersion()
            _isUpdateRequired.value = minimum != null && current < minimum
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.APP_VERSION)
        }
    }
}
