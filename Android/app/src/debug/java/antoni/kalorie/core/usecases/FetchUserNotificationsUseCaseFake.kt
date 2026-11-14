package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.UserNotificationDomain

data class FetchUserNotificationsUseCaseFake(
    val stubbedNotifications: List<UserNotificationDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchUserNotificationsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<UserNotificationDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedNotifications
    }
}
