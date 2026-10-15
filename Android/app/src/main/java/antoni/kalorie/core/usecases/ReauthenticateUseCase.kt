package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException

sealed class ReauthenticateError : Exception() {
    data object WrongAccount : ReauthenticateError()
}

interface ReauthenticateUseCaseProtocol {
    suspend operator fun invoke()
}

class ReauthenticateUseCase(
    private val googleSignInProvider: GoogleSignInProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : ReauthenticateUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        val credential = when (providerKind()) {
            AuthProviderKind.GOOGLE -> googleSignInProvider.signIn().credential
            AuthProviderKind.APPLE -> throw AuthError.NotAuthenticated
        }
        try {
            authCommandProvider.reauthenticate(credential)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if ((error as? FirebaseAuthException)?.errorCode == USER_MISMATCH) throw ReauthenticateError.WrongAccount
            throw error
        }
    }

    private suspend fun providerKind(): AuthProviderKind {
        val linkedKinds = authProvider.linkedProviderKinds
        val sessionKind = authProvider.currentSessionProviderKind()
        if (sessionKind != null && sessionKind in linkedKinds) return sessionKind
        return linkedKinds.singleOrNull() ?: throw AuthError.NotAuthenticated
    }

    private companion object {
        const val USER_MISMATCH = "ERROR_USER_MISMATCH"
    }
}
