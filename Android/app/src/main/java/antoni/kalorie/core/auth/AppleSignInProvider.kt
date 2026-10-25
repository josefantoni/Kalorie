package antoni.kalorie.core.auth

import android.app.Activity
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.tasks.await

sealed class AppleSignInError : Exception() {
    data object NoPresentingActivity : AppleSignInError()
}

data class AppleAuthResult(
    val displayName: String?,
    val email: String?,
    val accessToken: String?,
)

interface AppleSignInProviderProtocol {
    suspend fun link(): AppleAuthResult
    suspend fun reauthenticate(): AppleAuthResult
}

class AppleSignInProvider(
    private val currentActivityProvider: CurrentActivityProvider,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AppleSignInProviderProtocol {

    // MARK: - Functions

    override suspend fun link(): AppleAuthResult {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        return user.startActivityForLinkWithProvider(presentingActivity(), makeProvider()).await().toAppleAuthResult()
    }

    override suspend fun reauthenticate(): AppleAuthResult {
        val user = auth.currentUser ?: throw AuthError.NotAuthenticated
        return user.startActivityForReauthenticateWithProvider(presentingActivity(), makeProvider()).await().toAppleAuthResult()
    }

    // MARK: - Private

    private fun presentingActivity(): Activity = currentActivityProvider.activity ?: throw AppleSignInError.NoPresentingActivity

    private fun makeProvider() = OAuthProvider.newBuilder(Constants.Auth.APPLE_PROVIDER_ID).setScopes(listOf("email", "name")).build()

    private fun AuthResult.toAppleAuthResult() = AppleAuthResult(
        displayName = user?.displayName,
        email = user?.email,
        accessToken = (credential as? OAuthCredential)?.accessToken,
    )
}
