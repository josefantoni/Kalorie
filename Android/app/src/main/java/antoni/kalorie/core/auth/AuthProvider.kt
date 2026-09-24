package antoni.kalorie.core.auth

import com.google.firebase.auth.FirebaseAuth

sealed class AuthError : Exception() {
    data object NotAuthenticated : AuthError()
}

interface AuthProviderProtocol {
    val userId: String?
}

class AuthProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthProviderProtocol {

    // MARK: - Properties

    override val userId: String?
        get() = auth.currentUser?.uid
}
