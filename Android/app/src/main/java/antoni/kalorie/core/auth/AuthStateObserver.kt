package antoni.kalorie.core.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import antoni.kalorie.core.usecases.MigrateAnonymousDataUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface MergeStatusReporting {
    fun beginMerge()
    fun endMerge()
}

class AuthStateObserver(
    private val auth: FirebaseAuth,
    private val resumePendingMerge: MigrateAnonymousDataUseCaseProtocol,
) : ViewModel(), MergeStatusReporting {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Loading)
    val state: StateFlow<LoadingState<Unit>> = _state

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging

    private var hasAttemptedPendingMergeResume = false
    private var activeMergeCount = 0

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            viewModelScope.launch {
                resumePendingMergeOnce()
                _userId.value = user.uid
                _state.value = LoadingState.loaded
            }
        } else {
            signIn()
        }
    }

    // MARK: - Init

    init {
        auth.addAuthStateListener(authStateListener)
    }

    // MARK: - Functions

    fun retry() {
        _state.value = LoadingState.Loading
        signIn()
    }

    override fun beginMerge() {
        activeMergeCount += 1
        _isMerging.value = true
    }

    override fun endMerge() {
        activeMergeCount = maxOf(0, activeMergeCount - 1)
        _isMerging.value = activeMergeCount > 0
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
    }

    // MARK: - Private

    private suspend fun resumePendingMergeOnce() {
        if (hasAttemptedPendingMergeResume) return
        hasAttemptedPendingMergeResume = true
        beginMerge()
        try {
            resumePendingMerge.resumeIfNeeded()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.AUTH)
        } finally {
            endMerge()
        }
    }

    private fun signIn() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            _state.value = if (task.isSuccessful) LoadingState.loaded else LoadingState.Error(task.exception)
        }
    }
}
