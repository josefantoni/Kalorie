package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.GoogleSignInError
import antoni.kalorie.core.auth.GoogleSignInProviderFake
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ReauthenticateUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_whenLinkedWithGoogle_reauthenticatesThroughGoogle() = runTest {
        val (sut, authCommandProvider) = makeSUT(linkedProviderKinds = setOf(AuthProviderKind.GOOGLE))

        sut()

        assertEquals(1, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenNoProviderLinked_throwsNotAuthenticatedAndCallsNothing() = runTest {
        val (sut, authCommandProvider) = makeSUT(linkedProviderKinds = emptySet())

        try {
            sut()
            fail("Expected AuthError.NotAuthenticated to be thrown")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertEquals("there is nothing to reauthenticate without a linked provider", 0, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenGoogleSignInFails_propagatesErrorWithoutReauthenticating() = runTest {
        val (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds = setOf(AuthProviderKind.GOOGLE),
            googleSignInProvider = GoogleSignInProviderFake(errorToThrow = GoogleSignInError.UnexpectedCredential),
        )

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: GoogleSignInError.UnexpectedCredential) {
        }

        assertEquals("reauthenticate must not be called without a fresh credential", 0, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenReauthenticateFails_propagatesError() = runTest {
        val authCommandProvider = AuthCommandProviderFake()
        authCommandProvider.reauthenticateError = RuntimeException("offline")
        val sut = ReauthenticateUseCase(
            googleSignInProvider = GoogleSignInProviderFake(),
            authCommandProvider = authCommandProvider,
            authProvider = AuthProviderFake(linkedProviderKinds = setOf(AuthProviderKind.GOOGLE)),
        )

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }
    }

    @Test
    fun invoke_whenBothProvidersLinked_usesTheProviderOfTheCurrentSession() = runTest {
        val (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds = setOf(AuthProviderKind.APPLE, AuthProviderKind.GOOGLE),
            sessionProviderKind = AuthProviderKind.GOOGLE,
        )

        sut()

        assertEquals("the session provider is always valid, so the user is never asked to pick one", 1, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenSessionProviderIsUnknownAndOneIsLinked_fallsBackToTheLinkedOne() = runTest {
        val (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds = setOf(AuthProviderKind.GOOGLE),
            sessionProviderKind = null,
        )

        sut()

        assertEquals(1, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenSessionProviderIsUnknownAndBothAreLinked_throwsNotAuthenticated() = runTest {
        val (sut, authCommandProvider) = makeSUT(
            linkedProviderKinds = setOf(AuthProviderKind.APPLE, AuthProviderKind.GOOGLE),
            sessionProviderKind = null,
        )

        try {
            sut()
            fail("Expected AuthError.NotAuthenticated to be thrown")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertEquals("guessing a provider could ask the user to sign in with the wrong identity in a destructive flow", 0, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenUserPicksADifferentIdentity_throwsWrongAccount() = runTest {
        val authCommandProvider = AuthCommandProviderFake()
        authCommandProvider.reauthenticateError = FirebaseAuthException("ERROR_USER_MISMATCH", "mismatch")
        val sut = ReauthenticateUseCase(
            googleSignInProvider = GoogleSignInProviderFake(),
            authCommandProvider = authCommandProvider,
            authProvider = AuthProviderFake(linkedProviderKinds = setOf(AuthProviderKind.GOOGLE)),
        )

        try {
            sut()
            fail("Expected ReauthenticateError.WrongAccount to be thrown")
        } catch (_: ReauthenticateError.WrongAccount) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(
        linkedProviderKinds: Set<AuthProviderKind>,
        sessionProviderKind: AuthProviderKind? = null,
        googleSignInProvider: GoogleSignInProviderProtocol = GoogleSignInProviderFake(),
    ): Pair<ReauthenticateUseCase, AuthCommandProviderFake> {
        val authCommandProvider = AuthCommandProviderFake()
        val sut = ReauthenticateUseCase(
            googleSignInProvider = googleSignInProvider,
            authCommandProvider = authCommandProvider,
            authProvider = AuthProviderFake(linkedProviderKinds = linkedProviderKinds, sessionProviderKind = sessionProviderKind),
        )
        return sut to authCommandProvider
    }
}
