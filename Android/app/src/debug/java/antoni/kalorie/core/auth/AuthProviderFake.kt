package antoni.kalorie.core.auth

import java.time.Instant

data class AuthProviderFake(
    override val userId: String? = "test-user-id",
    override val isAnonymous: Boolean = true,
    override val displayName: String? = null,
    override val lastSignInDate: Instant? = Instant.now(),
    override val linkedProviderKinds: Set<AuthProviderKind> = emptySet(),
    val sessionProviderKind: AuthProviderKind? = null,
) : AuthProviderProtocol {

    override suspend fun currentSessionProviderKind(): AuthProviderKind? = sessionProviderKind
}
