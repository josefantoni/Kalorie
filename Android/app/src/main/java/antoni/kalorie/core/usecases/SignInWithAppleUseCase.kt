package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleAuthResult
import antoni.kalorie.core.auth.AppleSignInProviderProtocol
import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.UserProfileDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException

interface SignInWithAppleUseCaseProtocol {
    suspend operator fun invoke()
}

class SignInWithAppleUseCase(
    private val appleSignInProvider: AppleSignInProviderProtocol,
    private val linkOrMergeCredential: LinkOrMergeCredentialUseCaseProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SignInWithAppleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        var result: AppleAuthResult? = null
        linkOrMergeCredential.linkOrMerge { result = appleSignInProvider.link() }
        result?.let { saveProfileIfNeeded(displayName = it.displayName, email = it.email) }
    }

    suspend fun saveProfileIfNeeded(displayName: String?, email: String?) {
        if (displayName == null && email == null) return
        if (displayName != null) {
            try {
                authCommandProvider.updateDisplayName(displayName)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.AUTH)
            }
        }
        val userId = authProvider.userId ?: return
        try {
            dataProvider.setAsync(UserProfileDTO(displayName = displayName, email = email), id = userId, inCollection = Constants.Firestore.USERS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.AUTH)
        }
    }
}
