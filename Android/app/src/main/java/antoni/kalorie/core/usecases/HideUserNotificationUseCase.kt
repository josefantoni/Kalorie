package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.NotificationHiddenDTO
import antoni.kalorie.core.networking.updateAsync

interface HideUserNotificationUseCaseProtocol {
    suspend operator fun invoke(notification: UserNotificationDomain)
}

class HideUserNotificationUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : HideUserNotificationUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(notification: UserNotificationDomain) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        dataProvider.updateAsync(
            NotificationHiddenDTO(),
            id = notification.id,
            inCollection = notification.kind.collection,
        )
    }
}
