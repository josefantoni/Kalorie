package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol

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
        val credential = when (authProvider.linkedProviderKind) {
            AuthProviderKind.GOOGLE -> googleSignInProvider.signIn().credential
            null -> throw AuthError.NotAuthenticated
        }
        authCommandProvider.reauthenticate(credential)
    }
}
