package antoni.kalorie.core.auth

import com.google.firebase.auth.GoogleAuthProvider

data class GoogleSignInProviderFake(
    val result: GoogleSignInResult = GoogleSignInResult(
        credential = GoogleAuthProvider.getCredential("fake-id-token", null),
        displayName = "Josef Antoni",
        email = "josef@example.com",
    ),
    val errorToThrow: Exception? = null,
) : GoogleSignInProviderProtocol {

    // MARK: - Functions

    override suspend fun signIn(): GoogleSignInResult {
        errorToThrow?.let { throw it }
        return result
    }
}
