package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderProtocol

interface SignInWithAppleUseCaseProtocol {
    suspend operator fun invoke()
}

class SignInWithAppleUseCase(
    private val appleSignInProvider: AppleSignInProviderProtocol,
    private val linkOrMergeCredential: LinkOrMergeCredentialUseCaseProtocol,
    private val fillEmptyProfile: FillEmptyProfileUseCaseProtocol,
) : SignInWithAppleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        var result: AppleAuthResult? = null
        linkOrMergeCredential.linkOrMerge { result = appleSignInProvider.link() }
        result?.let { fillEmptyProfile(displayName = it.displayName, email = it.email) }
    }
}
