package antoni.kalorie.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager

interface GoogleSessionProviderProtocol {
    suspend fun clearSession()
}

class GoogleSessionProvider(private val context: Context) : GoogleSessionProviderProtocol {

    // MARK: - Functions

    override suspend fun clearSession() {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
