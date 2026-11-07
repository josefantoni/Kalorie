package antoni.kalorie.core.usecases

data class FetchHasPendingNotificationsUseCaseFake(
    val hasPending: Boolean = false,
) : FetchHasPendingNotificationsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean = hasPending
}
