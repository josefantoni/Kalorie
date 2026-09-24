package antoni.kalorie.core.auth

data class AuthProviderFake(
    override val userId: String? = "test-user-id",
) : AuthProviderProtocol
