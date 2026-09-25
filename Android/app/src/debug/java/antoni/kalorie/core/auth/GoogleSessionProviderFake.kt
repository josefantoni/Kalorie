package antoni.kalorie.core.auth

class GoogleSessionProviderFake : GoogleSessionProviderProtocol {

    // MARK: - Properties

    var clearSessionCallCount = 0
        private set

    // MARK: - Functions

    override suspend fun clearSession() {
        clearSessionCallCount += 1
    }
}
