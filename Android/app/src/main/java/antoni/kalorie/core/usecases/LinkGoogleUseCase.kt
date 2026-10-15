package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.GoogleSignInProviderProtocol

interface LinkGoogleUseCaseProtocol {
    suspend operator fun invoke()
}

class LinkGoogleUseCase(
    private val googleSignInProvider: GoogleSignInProviderProtocol,
    private val linkCredential: LinkCredentialUseCaseProtocol,
    private val fillEmptyProfile: FillEmptyProfileUseCaseProtocol,
) : LinkGoogleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        val result = googleSignInProvider.signIn()
        linkCredential(result.credential)
        fillEmptyProfile(displayName = result.displayName, email = result.email)
    }
}
