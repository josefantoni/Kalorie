package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.NotificationSeenDTO
import antoni.kalorie.core.networking.updateAsync
import java.time.Instant

interface MarkUserNotificationSeenUseCaseProtocol {
    suspend operator fun invoke(notification: UserNotificationDomain)
}

class MarkUserNotificationSeenUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : MarkUserNotificationSeenUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(notification: UserNotificationDomain) {
        if (!notification.isUnread) return
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        dataProvider.updateAsync(
            NotificationSeenDTO(seenAt = Instant.now()),
            id = notification.id,
            inCollection = notification.kind.collection,
        )
    }
}
