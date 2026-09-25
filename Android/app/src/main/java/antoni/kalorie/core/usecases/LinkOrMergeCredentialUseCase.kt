package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthProviderProtocol
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CancellationException

sealed class LinkOrMergeCredentialError : Exception() {
    data object AccountExistsWithAnotherProvider : LinkOrMergeCredentialError()
}

interface LinkOrMergeCredentialUseCaseProtocol {
    suspend operator fun invoke(credential: AuthCredential)
}

class LinkOrMergeCredentialUseCase(
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val migrateAnonymousData: MigrateAnonymousDataUseCaseProtocol,
) : LinkOrMergeCredentialUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(credential: AuthCredential) {
        val anonymousUserId = authProvider.userId
        try {
            authCommandProvider.link(credential)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val errorCode = (error as? FirebaseAuthUserCollisionException)?.errorCode
            if (errorCode == EMAIL_ALREADY_IN_USE) throw LinkOrMergeCredentialError.AccountExistsWithAnotherProvider
            if (anonymousUserId == null || errorCode != CREDENTIAL_ALREADY_IN_USE) throw error
            migrateAnonymousData.migrate(anonymousUserId, credential)
        }
    }

    private companion object {
        const val EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
        const val CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"
    }
}
