package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderProtocol

interface LinkAppleUseCaseProtocol {
    suspend operator fun invoke()
}

class LinkAppleUseCase(
    private val appleSignInProvider: AppleSignInProviderProtocol,
    private val linkCredential: LinkCredentialUseCaseProtocol,
    private val fillEmptyProfile: FillEmptyProfileUseCaseProtocol,
) : LinkAppleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        var result: AppleAuthResult? = null
        linkCredential.link { result = appleSignInProvider.link() }
        result?.let { fillEmptyProfile(displayName = it.displayName, email = it.email) }
    }
}
