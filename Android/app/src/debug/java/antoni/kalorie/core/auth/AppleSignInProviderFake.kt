package antoni.kalorie.core.auth

class AppleSignInProviderFake(
    var result: AppleAuthResult = AppleAuthResult(displayName = null, email = null, accessToken = "fake-access-token"),
    var linkError: Exception? = null,
    var reauthenticateError: Exception? = null,
) : AppleSignInProviderProtocol {

    // MARK: - Properties

    var linkCallCount = 0
        private set
    var reauthenticateCallCount = 0
        private set

    // MARK: - Functions

    override suspend fun link(): AppleAuthResult {
        linkCallCount += 1
        linkError?.let { throw it }
        return result
    }

    override suspend fun reauthenticate(): AppleAuthResult {
        reauthenticateCallCount += 1
        reauthenticateError?.let { throw it }
        return result
    }
}
