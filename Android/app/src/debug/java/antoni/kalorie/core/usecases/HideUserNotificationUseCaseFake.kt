package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.UserNotificationDomain

data class HideUserNotificationUseCaseFake(
    val errorToThrow: Exception? = null,
) : HideUserNotificationUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(notification: UserNotificationDomain) {
        errorToThrow?.let { throw it }
    }
}
