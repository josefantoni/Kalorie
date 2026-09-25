package antoni.kalorie.core.auth

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

interface AuthCommandProviderProtocol {
    suspend fun link(credential: AuthCredential)
    suspend fun signIn(credential: AuthCredential)
    suspend fun reauthenticate(credential: AuthCredential)
    fun signOut()
    suspend fun updateDisplayName(name: String)
    suspend fun deleteCurrentUser()
}

class AuthCommandProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthCommandProviderProtocol {

    // MARK: - Functions

    override suspend fun link(credential: AuthCredential) {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        user.linkWithCredential(credential).await()
    }

    override suspend fun signIn(credential: AuthCredential) {
        auth.signInWithCredential(credential).await()
    }

    override suspend fun reauthenticate(credential: AuthCredential) {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        user.reauthenticate(credential).await()
    }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        user.updateProfile(userProfileChangeRequest { displayName = name }).await()
    }

    override suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        user.delete().await()
    }
}
