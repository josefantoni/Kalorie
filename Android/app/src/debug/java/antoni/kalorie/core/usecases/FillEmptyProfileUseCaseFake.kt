package antoni.kalorie.core.usecases

class FillEmptyProfileUseCaseFake : FillEmptyProfileUseCaseProtocol {

    // MARK: - Properties

    var callCount = 0
        private set
    var receivedDisplayName: String? = null
        private set
    var receivedEmail: String? = null
        private set

    // MARK: - Functions

    override suspend fun invoke(displayName: String?, email: String?) {
        callCount += 1
        receivedDisplayName = displayName
        receivedEmail = email
    }
}
