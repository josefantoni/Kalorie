package antoni.kalorie.core.auth

import com.google.firebase.auth.AuthCredential

class AuthCommandProviderFake : AuthCommandProviderProtocol {

    // MARK: - Properties

    var linkError: Exception? = null
    var signInError: Exception? = null
    var reauthenticateError: Exception? = null
    var signOutError: Exception? = null
    var deleteError: Exception? = null
    var onDelete: (() -> Unit)? = null
    var linkCallCount = 0
        private set
    var signInCallCount = 0
        private set
    var reauthenticateCallCount = 0
        private set
    var signOutCallCount = 0
        private set
    var updateDisplayNameCallCount = 0
        private set
    var deleteCallCount = 0
        private set

    // MARK: - Functions

    override suspend fun link(credential: AuthCredential) {
        linkCallCount += 1
        linkError?.let { throw it }
    }

    override suspend fun signIn(credential: AuthCredential) {
        signInCallCount += 1
        signInError?.let { throw it }
    }

    override suspend fun reauthenticate(credential: AuthCredential) {
        reauthenticateCallCount += 1
        reauthenticateError?.let { throw it }
    }

    override fun signOut() {
        signOutCallCount += 1
        signOutError?.let { throw it }
    }

    override suspend fun updateDisplayName(name: String) {
        updateDisplayNameCallCount += 1
    }

    override suspend fun deleteCurrentUser() {
        deleteCallCount += 1
        onDelete?.invoke()
        deleteError?.let { throw it }
    }
}
