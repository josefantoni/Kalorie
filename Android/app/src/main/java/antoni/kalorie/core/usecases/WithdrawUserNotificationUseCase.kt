package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.isFirestorePermissionDenied
import kotlinx.coroutines.CancellationException

sealed class WithdrawUserNotificationError : Exception() {
    data object AlreadyResolved : WithdrawUserNotificationError()
}

interface WithdrawUserNotificationUseCaseProtocol {
    suspend operator fun invoke(notification: UserNotificationDomain)
}

class WithdrawUserNotificationUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    storageProvider: StorageDataProviderProtocol,
) : WithdrawUserNotificationUseCaseProtocol {

    // MARK: - Properties

    private val remover = PhotoSuggestionRemover(storageProvider)

    // MARK: - Functions

    override suspend fun invoke(notification: UserNotificationDomain) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        if (notification.status != TicketStatus.PENDING) throw WithdrawUserNotificationError.AlreadyResolved
        try {
            when (notification.kind) {
                UserNotificationDomain.Kind.REPORT -> dataProvider.deleteAsync(
                    id = notification.id,
                    from = Constants.Firestore.FOOD_ITEM_REPORTS,
                )
                UserNotificationDomain.Kind.PHOTO_SUGGESTION -> {
                    notification.photoUrl?.let { remover.deleteFile(it) }
                    dataProvider.deleteAsync(id = notification.id, from = Constants.Firestore.PHOTO_SUGGESTIONS)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error.isFirestorePermissionDenied) throw WithdrawUserNotificationError.AlreadyResolved
            throw error
        }
    }
}
