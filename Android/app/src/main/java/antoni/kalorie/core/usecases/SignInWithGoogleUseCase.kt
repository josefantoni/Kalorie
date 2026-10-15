package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.GoogleSignInProviderProtocol

interface SignInWithGoogleUseCaseProtocol {
    suspend operator fun invoke()
}

class SignInWithGoogleUseCase(
    private val googleSignInProvider: GoogleSignInProviderProtocol,
    private val linkOrMergeCredential: LinkOrMergeCredentialUseCaseProtocol,
    private val fillEmptyProfile: FillEmptyProfileUseCaseProtocol,
) : SignInWithGoogleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        val result = googleSignInProvider.signIn()
        linkOrMergeCredential(result.credential)
        fillEmptyProfile(displayName = result.displayName, email = result.email)
    }
}
