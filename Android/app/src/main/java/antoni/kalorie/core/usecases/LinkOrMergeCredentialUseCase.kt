package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CancellationException

sealed class LinkOrMergeCredentialError : Exception() {
    data object AccountExistsWithAnotherProvider : LinkOrMergeCredentialError()
    data object NotAnonymous : LinkOrMergeCredentialError()
}

interface LinkOrMergeCredentialUseCaseProtocol {
    suspend operator fun invoke(credential: AuthCredential)
    suspend fun linkOrMerge(link: suspend () -> Unit)
}

class LinkOrMergeCredentialUseCase(
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val migrateAnonymousData: MigrateAnonymousDataUseCaseProtocol,
) : LinkOrMergeCredentialUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(credential: AuthCredential) {
        run(link = { authCommandProvider.link(credential) }, mergeCredential = { credential })
    }

    override suspend fun linkOrMerge(link: suspend () -> Unit) {
        run(link = link, mergeCredential = { (it as? FirebaseAuthUserCollisionException)?.updatedCredential })
    }

    // MARK: - Private

    private suspend fun run(link: suspend () -> Unit, mergeCredential: (Exception) -> AuthCredential?) {
        if (!authProvider.isAnonymous) throw LinkOrMergeCredentialError.NotAnonymous
        val anonymousUserId = authProvider.userId
        try {
            link()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val errorCode = (error as? FirebaseAuthUserCollisionException)?.errorCode
            if (errorCode == Constants.Auth.ErrorCode.EMAIL_ALREADY_IN_USE) throw LinkOrMergeCredentialError.AccountExistsWithAnotherProvider
            if (anonymousUserId == null || errorCode != Constants.Auth.ErrorCode.CREDENTIAL_ALREADY_IN_USE) throw error
            val credential = mergeCredential(error) ?: throw error
            migrateAnonymousData.migrate(anonymousUserId, credential)
        }
    }
}
