package antoni.kalorie.core.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.time.Instant

sealed class AuthError : Exception() {
    data object NotAuthenticated : AuthError()
}

enum class AuthProviderKind {
    APPLE,
    GOOGLE,
}

interface AuthProviderProtocol {
    val userId: String?
    val isAnonymous: Boolean
    val displayName: String?
    val providerDisplayName: String?
    val linkedProviderKinds: Set<AuthProviderKind>

    suspend fun currentSessionProviderKind(): AuthProviderKind?

    suspend fun currentAuthenticationDate(): Instant?
}

class AuthProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthProviderProtocol {

    // MARK: - Properties

    override val userId: String?
        get() = auth.currentUser?.uid

    override val isAnonymous: Boolean
        get() = auth.currentUser?.isAnonymous ?: true

    override val displayName: String?
        get() = auth.currentUser?.displayName

    override val providerDisplayName: String?
        get() = auth.currentUser?.providerData.orEmpty().firstNotNullOfOrNull { it.displayName?.takeIf(String::isNotEmpty) }

    override val linkedProviderKinds: Set<AuthProviderKind>
        get() = auth.currentUser?.providerData.orEmpty().mapNotNull { kind(it.providerId) }.toSet()

    // MARK: - Functions

    override suspend fun currentSessionProviderKind(): AuthProviderKind? {
        val user = auth.currentUser ?: return null
        val signInProvider = try {
            user.getIdToken(false).await().signInProvider
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        return signInProvider?.let(::kind)
    }

    override suspend fun currentAuthenticationDate(): Instant? {
        val user = auth.currentUser ?: return null
        return try {
            Instant.ofEpochSecond(user.getIdToken(true).await().authTimestamp)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun kind(providerId: String): AuthProviderKind? = when (providerId) {
        APPLE_PROVIDER_ID -> AuthProviderKind.APPLE
        GoogleAuthProvider.PROVIDER_ID -> AuthProviderKind.GOOGLE
        else -> null
    }

    private companion object {
        const val APPLE_PROVIDER_ID = "apple.com"
    }
}
