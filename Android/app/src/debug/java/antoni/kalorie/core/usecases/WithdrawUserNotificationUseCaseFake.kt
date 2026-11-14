package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.UserNotificationDomain

data class WithdrawUserNotificationUseCaseFake(
    val errorToThrow: Exception? = null,
) : WithdrawUserNotificationUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(notification: UserNotificationDomain) {
        errorToThrow?.let { throw it }
    }
}
