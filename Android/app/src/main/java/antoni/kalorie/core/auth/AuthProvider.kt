package antoni.kalorie.core.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.time.Instant

sealed class AuthError : Exception() {
    data object NotAuthenticated : AuthError()
}

enum class AuthProviderKind {
    GOOGLE,
}

interface AuthProviderProtocol {
    val userId: String?
    val isAnonymous: Boolean
    val displayName: String?
    val lastSignInDate: Instant?
    val linkedProviderKind: AuthProviderKind?
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

    override val lastSignInDate: Instant?
        get() = auth.currentUser?.metadata?.lastSignInTimestamp?.let(Instant::ofEpochMilli)

    override val linkedProviderKind: AuthProviderKind?
        get() = when (auth.currentUser?.providerData?.firstOrNull { it.providerId != FIREBASE_PROVIDER_ID }?.providerId) {
            GoogleAuthProvider.PROVIDER_ID -> AuthProviderKind.GOOGLE
            else -> null
        }

    private companion object {
        const val FIREBASE_PROVIDER_ID = "firebase"
    }
}
