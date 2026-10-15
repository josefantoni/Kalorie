package antoni.kalorie.core.usecases

import com.google.firebase.auth.AuthCredential

data class LinkCredentialUseCaseFake(
    val errorToThrow: Exception? = null,
) : LinkCredentialUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(credential: AuthCredential) {
        errorToThrow?.let { throw it }
    }

    override suspend fun link(operation: suspend () -> Unit) {
        errorToThrow?.let { throw it }
        operation()
    }
}
