package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.UserProfileDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException

interface SignInWithGoogleUseCaseProtocol {
    suspend operator fun invoke()
}

class SignInWithGoogleUseCase(
    private val googleSignInProvider: GoogleSignInProviderProtocol,
    private val linkOrMergeCredential: LinkOrMergeCredentialUseCaseProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SignInWithGoogleUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        val result = googleSignInProvider.signIn()
        linkOrMergeCredential(result.credential)
        saveProfileIfNeeded(displayName = result.displayName, email = result.email)
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
