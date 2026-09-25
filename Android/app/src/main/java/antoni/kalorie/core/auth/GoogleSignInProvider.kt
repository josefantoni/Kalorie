package antoni.kalorie.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import antoni.kalorie.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

sealed class GoogleSignInError : Exception() {
    data object NoPresentingActivity : GoogleSignInError()
    data object UnexpectedCredential : GoogleSignInError()
}

data class GoogleSignInResult(
    val credential: AuthCredential,
    val displayName: String?,
    val email: String?,
)

interface GoogleSignInProviderProtocol {
    suspend fun signIn(): GoogleSignInResult
}

class GoogleSignInProvider(
    private val context: Context,
    private val currentActivityProvider: CurrentActivityProvider,
) : GoogleSignInProviderProtocol {

    // MARK: - Functions

    override suspend fun signIn(): GoogleSignInResult {
        val activity = currentActivityProvider.activity ?: throw GoogleSignInError.NoPresentingActivity
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(context).getCredential(activity, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleSignInError.UnexpectedCredential
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleSignInResult(
            credential = GoogleAuthProvider.getCredential(googleCredential.idToken, null),
            displayName = googleCredential.displayName,
            email = googleCredential.id,
        )
    }
}
