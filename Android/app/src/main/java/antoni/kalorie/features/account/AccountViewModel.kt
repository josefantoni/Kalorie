package antoni.kalorie.features.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.MergeStatusReporting
import antoni.kalorie.core.usecases.DeleteAccountError
import antoni.kalorie.core.usecases.DeleteAccountUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.usecases.LinkOrMergeCredentialError
import antoni.kalorie.core.usecases.ReauthenticateUseCaseProtocol
import antoni.kalorie.core.usecases.SignInWithGoogleUseCaseProtocol
import antoni.kalorie.core.usecases.SignOutUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AccountViewModel(
    private val authProvider: AuthProviderProtocol,
    private val signOut: SignOutUseCaseProtocol,
    private val signInWithGoogle: SignInWithGoogleUseCaseProtocol,
    private val deleteAccount: DeleteAccountUseCaseProtocol,
    private val reauthenticate: ReauthenticateUseCaseProtocol,
    private val fetchMaintainerClaim: FetchMaintainerClaimUseCaseProtocol,
    private val mergeStatusReporting: MergeStatusReporting,
) : ViewModel() {

    // MARK: - State

    enum class State {
        IDLE,
        LINKING,
        DELETING_ACCOUNT,
    }

    // MARK: - Properties

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val showDeleteConfirmation = MutableStateFlow(false)
    val isReauthenticateAlertVisible = MutableStateFlow(false)
    private val _isMaintainer = MutableStateFlow(false)
    val isMaintainer: StateFlow<Boolean> = _isMaintainer
    private var isDataAlreadyWiped = false

    private val _isAnonymous = MutableStateFlow(authProvider.isAnonymous)
    val isAnonymous: StateFlow<Boolean> = _isAnonymous
    private val _displayName = MutableStateFlow(authProvider.displayName)
    val displayName: StateFlow<String?> = _displayName

    // MARK: - Functions

    suspend fun onAppear() {
        try {
            _isMaintainer.value = fetchMaintainerClaim()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ACCOUNT)
        }
    }

    suspend fun onSignOutTapped() {
        try {
            signOut()
            _isMaintainer.value = false
            refreshAuthState()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ACCOUNT)
            alertItem.value = AlertItem(titleRes = R.string.account_error_signOutFailed)
        }
    }

    suspend fun onSignInWithGoogleTapped() {
        _state.value = State.LINKING
        mergeStatusReporting.beginMerge()
        try {
            signInWithGoogle()
        } catch (error: CancellationException) {
            throw error
        } catch (_: LinkOrMergeCredentialError.AccountExistsWithAnotherProvider) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_accountExistsWithApple)
        } catch (error: Exception) {
            if (!isUserCancellation(error)) {
                Log.error(error, Constants.LogCategory.ACCOUNT)
                alertItem.value = AlertItem(titleRes = R.string.account_error_signInFailed)
            }
        } finally {
            mergeStatusReporting.endMerge()
            refreshAuthState()
            _state.value = State.IDLE
        }
    }

    suspend fun onDeleteAccountConfirmed() {
        isDataAlreadyWiped = false
        try {
            performDelete()
        } finally {
            refreshAuthState()
            _state.value = State.IDLE
        }
    }

    suspend fun onReauthenticateConfirmed() {
        _state.value = State.LINKING
        try {
            reauthenticate()
            performDelete()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!isUserCancellation(error)) {
                Log.error(error, Constants.LogCategory.ACCOUNT)
                alertItem.value = AlertItem(titleRes = R.string.account_error_signInFailed)
            }
        } finally {
            refreshAuthState()
            _state.value = State.IDLE
        }
    }

    // MARK: - Private

    private fun refreshAuthState() {
        _isAnonymous.value = authProvider.isAnonymous
        _displayName.value = authProvider.displayName
    }

    private fun isUserCancellation(error: Throwable): Boolean = error is GetCredentialCancellationException

    private suspend fun performDelete() {
        _state.value = State.DELETING_ACCOUNT
        try {
            deleteAccount(isDataAlreadyWiped)
        } catch (error: CancellationException) {
            throw error
        } catch (error: DeleteAccountError.RequiresRecentLogin) {
            isDataAlreadyWiped = error.dataAlreadyDeleted
            isReauthenticateAlertVisible.value = true
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ACCOUNT)
            alertItem.value = AlertItem(titleRes = R.string.account_error_deleteFailed)
        }
    }
}
