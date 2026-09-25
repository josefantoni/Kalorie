package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.GoogleSignInError
import antoni.kalorie.core.auth.GoogleSignInProviderFake
import antoni.kalorie.core.auth.GoogleSignInProviderProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ReauthenticateUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_whenLinkedWithGoogle_reauthenticatesThroughGoogle() = runTest {
        val (sut, authCommandProvider) = makeSUT(linkedProviderKind = AuthProviderKind.GOOGLE)

        sut()

        assertEquals(1, authCommandProvider.reauthenticateCallCount)
    }

    @Test
    fun invoke_whenNoProviderLinked_throwsNotAuthenticatedAndCallsNothing() = runTest {
        val (sut, authCommandProvider) = makeSUT(linkedProviderKind = null)

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
            linkedProviderKind = AuthProviderKind.GOOGLE,
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
            authProvider = AuthProviderFake(linkedProviderKind = AuthProviderKind.GOOGLE),
        )

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(
        linkedProviderKind: AuthProviderKind?,
        googleSignInProvider: GoogleSignInProviderProtocol = GoogleSignInProviderFake(),
    ): Pair<ReauthenticateUseCase, AuthCommandProviderFake> {
        val authCommandProvider = AuthCommandProviderFake()
        val sut = ReauthenticateUseCase(
            googleSignInProvider = googleSignInProvider,
            authCommandProvider = authCommandProvider,
            authProvider = AuthProviderFake(linkedProviderKind = linkedProviderKind),
        )
        return sut to authCommandProvider
    }
}
