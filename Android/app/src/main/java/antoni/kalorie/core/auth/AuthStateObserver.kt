package antoni.kalorie.core.auth

import androidx.lifecycle.ViewModel
import antoni.kalorie.core.utils.LoadingState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthStateObserver(private val auth: FirebaseAuth) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Loading)
    val state: StateFlow<LoadingState<Unit>> = _state

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _userId.value = user.uid
            _state.value = LoadingState.loaded
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

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
    }

    // MARK: - Private

    private fun signIn() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            _state.value = if (task.isSuccessful) LoadingState.loaded else LoadingState.Error(task.exception)
        }
    }
}
