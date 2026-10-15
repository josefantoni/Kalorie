package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CancellationException

sealed class LinkCredentialError : Exception() {
    data object AlreadyLinked : LinkCredentialError()
    data object CredentialBelongsToAnotherAccount : LinkCredentialError()
    data object EmailBelongsToAnotherAccount : LinkCredentialError()
    data object MergeInProgress : LinkCredentialError()
}

interface LinkCredentialUseCaseProtocol {
    suspend operator fun invoke(credential: AuthCredential)
    suspend fun link(operation: suspend () -> Unit)
}

class LinkCredentialUseCase(
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val snapshotStore: PendingMergeSnapshotStoreProtocol,
) : LinkCredentialUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(credential: AuthCredential) {
        link { authCommandProvider.link(credential) }
    }

    override suspend fun link(operation: suspend () -> Unit) {
        if (authProvider.isAnonymous) throw AuthError.NotAuthenticated
        if (snapshotStore.load() != null) throw LinkCredentialError.MergeInProgress
        try {
            operation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val errorCode = (error as? FirebaseAuthException)?.errorCode
            when {
                errorCode == PROVIDER_ALREADY_LINKED -> throw LinkCredentialError.AlreadyLinked
                error is FirebaseAuthUserCollisionException && errorCode == CREDENTIAL_ALREADY_IN_USE -> throw LinkCredentialError.CredentialBelongsToAnotherAccount
                error is FirebaseAuthUserCollisionException && errorCode == EMAIL_ALREADY_IN_USE -> throw LinkCredentialError.EmailBelongsToAnotherAccount
                else -> throw error
            }
        }
    }

    private companion object {
        const val PROVIDER_ALREADY_LINKED = "ERROR_PROVIDER_ALREADY_LINKED"
        const val CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"
        const val EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
    }
}
