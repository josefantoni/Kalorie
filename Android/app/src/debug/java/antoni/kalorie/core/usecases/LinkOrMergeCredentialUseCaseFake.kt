package antoni.kalorie.core.usecases

import com.google.firebase.auth.AuthCredential

data class LinkOrMergeCredentialUseCaseFake(
    val shouldThrow: Boolean = false,
) : LinkOrMergeCredentialUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(credential: AuthCredential) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
