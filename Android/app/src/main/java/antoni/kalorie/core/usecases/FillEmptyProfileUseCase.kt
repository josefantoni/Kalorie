package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.UserProfileDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException

interface FillEmptyProfileUseCaseProtocol {
    suspend operator fun invoke(displayName: String?, email: String?)
}

class FillEmptyProfileUseCase(
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val dataProvider: FirestoreDataProviderProtocol,
) : FillEmptyProfileUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(displayName: String?, email: String?) {
        val userId = authProvider.userId ?: return
        val existing = try {
            dataProvider.loadAsync<UserProfileDTO>(id = userId, from = Constants.Firestore.USERS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.AUTH)
            return
        }

        val currentName = authProvider.displayName.nonEmpty() ?: existing?.displayName.nonEmpty()
        val currentEmail = existing?.email.nonEmpty()
        val nameToFill = if (currentName == null) displayName.nonEmpty() else null
        val emailToFill = if (currentEmail == null) email.nonEmpty() else null
        if (nameToFill == null && emailToFill == null) return

        if (nameToFill != null && authProvider.displayName.nonEmpty() == null) {
            try {
                authCommandProvider.updateDisplayName(nameToFill)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.AUTH)
            }
        }

        val profile = UserProfileDTO(displayName = currentName ?: nameToFill, email = currentEmail ?: emailToFill)
        try {
            dataProvider.setAsync(profile, id = userId, inCollection = Constants.Firestore.USERS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.AUTH)
        }
    }

    private fun String?.nonEmpty(): String? = this?.takeIf { it.isNotEmpty() }
}
