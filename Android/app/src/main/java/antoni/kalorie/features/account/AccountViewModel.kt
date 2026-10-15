package antoni.kalorie.features.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInError
import antoni.kalorie.core.auth.MergeStatusReporting
import antoni.kalorie.core.usecases.DeleteAccountError
import antoni.kalorie.core.usecases.DeleteAccountUseCaseProtocol
import antoni.kalorie.core.usecases.LinkAppleUseCaseProtocol
import antoni.kalorie.core.usecases.LinkCredentialError
import antoni.kalorie.core.usecases.LinkGoogleUseCaseProtocol
import antoni.kalorie.core.usecases.LinkOrMergeCredentialError
import antoni.kalorie.core.usecases.ReauthenticateError
import antoni.kalorie.core.usecases.ReauthenticateUseCaseProtocol
import antoni.kalorie.core.usecases.SignInWithAppleUseCaseProtocol
import antoni.kalorie.core.usecases.SignInWithGoogleUseCaseProtocol
import antoni.kalorie.core.usecases.SignOutUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.firebase.auth.FirebaseAuthWebException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AccountViewModel(
    private val authProvider: AuthProviderProtocol,
    private val signOut: SignOutUseCaseProtocol,
    private val signInWithApple: SignInWithAppleUseCaseProtocol,
    private val signInWithGoogle: SignInWithGoogleUseCaseProtocol,
    private val linkApple: LinkAppleUseCaseProtocol,
    private val linkGoogle: LinkGoogleUseCaseProtocol,
    private val deleteAccount: DeleteAccountUseCaseProtocol,
    private val reauthenticate: ReauthenticateUseCaseProtocol,
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
    private var isDataAlreadyWiped = false

    private val _isAnonymous = MutableStateFlow(authProvider.isAnonymous)
    val isAnonymous: StateFlow<Boolean> = _isAnonymous
    private val _displayName = MutableStateFlow(authProvider.displayName)
    val displayName: StateFlow<String?> = _displayName
    private val _linkedProviderKinds = MutableStateFlow(authProvider.linkedProviderKinds)
    val linkedProviderKinds: StateFlow<Set<AuthProviderKind>> = _linkedProviderKinds

    // MARK: - Functions

    suspend fun onSignOutTapped() {
        try {
            signOut()
            refreshAuthState()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ACCOUNT)
            alertItem.value = AlertItem(titleRes = R.string.account_error_signOutFailed)
        }
    }

    suspend fun onSignInWithAppleTapped() {
        _state.value = State.LINKING
        mergeStatusReporting.beginMerge()
        try {
            signInWithApple()
        } catch (error: CancellationException) {
            throw error
        } catch (_: LinkOrMergeCredentialError.AccountExistsWithAnotherProvider) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_accountExistsWithGoogle)
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

    suspend fun onSignInWithGoogleTapped() {
        _state.value = State.LINKING
        mergeStatusReporting.beginMerge()
        try {
            signInWithGoogle()
        } catch (error: CancellationException) {
            throw error
        } catch (_: LinkOrMergeCredentialError.AccountExistsWithAnotherProvider) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_accountExistsWithApple)
        } catch (_: GoogleSignInError.NoGoogleAccount) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_noGoogleAccount)
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

    suspend fun onLinkProviderTapped(kind: AuthProviderKind) {
        _state.value = State.LINKING
        try {
            when (kind) {
                AuthProviderKind.APPLE -> linkApple()
                AuthProviderKind.GOOGLE -> linkGoogle()
            }
            refreshAuthState()
            alertItem.value = AlertItem(titleRes = R.string.account_alert_linkSuccessTitle)
        } catch (error: CancellationException) {
            throw error
        } catch (_: LinkCredentialError.AlreadyLinked) {
            refreshAuthState()
            alertItem.value = AlertItem(titleRes = R.string.account_error_linkAlreadyLinked)
        } catch (_: LinkCredentialError.CredentialBelongsToAnotherAccount) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_linkCredentialBelongsToAnotherAccount)
        } catch (_: LinkCredentialError.EmailBelongsToAnotherAccount) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_linkEmailBelongsToAnotherAccount)
        } catch (_: LinkCredentialError.MergeInProgress) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_linkMergeInProgress)
        } catch (_: GoogleSignInError.NoGoogleAccount) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_noGoogleAccount)
        } catch (error: Exception) {
            if (!isUserCancellation(error)) {
                Log.error(error, Constants.LogCategory.ACCOUNT)
                alertItem.value = AlertItem(titleRes = R.string.account_error_signInFailed)
            }
        } finally {
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
        } catch (_: ReauthenticateError.WrongAccount) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_wrongAccount)
        } catch (_: GoogleSignInError.NoGoogleAccount) {
            alertItem.value = AlertItem(titleRes = R.string.account_error_noGoogleAccount)
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
        _linkedProviderKinds.value = authProvider.linkedProviderKinds
    }

    private fun isUserCancellation(error: Throwable): Boolean = error is GetCredentialCancellationException || (error is FirebaseAuthWebException && error.errorCode == WEB_CONTEXT_CANCELED)

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
            if (!isUserCancellation(error)) {
                Log.error(error, Constants.LogCategory.ACCOUNT)
                alertItem.value = AlertItem(titleRes = R.string.account_error_deleteFailed)
            }
        }
    }

    private companion object {
        const val WEB_CONTEXT_CANCELED = "ERROR_WEB_CONTEXT_CANCELED"
    }
}
