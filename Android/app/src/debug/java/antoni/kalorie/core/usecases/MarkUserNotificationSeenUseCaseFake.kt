package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.UserNotificationDomain

data class MarkUserNotificationSeenUseCaseFake(
    val errorToThrow: Exception? = null,
) : MarkUserNotificationSeenUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(notification: UserNotificationDomain) {
        errorToThrow?.let { throw it }
    }
}
