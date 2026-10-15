package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreFake
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LinkCredentialUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_whenSignedIn_linksTheCredential() = runTest {
        val (sut, authCommandProvider, _) = makeSUT()

        sut(makeCredential())

        assertEquals(1, authCommandProvider.linkCallCount)
    }

    @Test
    fun link_whenSignedIn_runsTheOperation() = runTest {
        val (sut, _, _) = makeSUT()
        var ran = false

        sut.link { ran = true }

        assertEquals(true, ran)
    }

    @Test
    fun invoke_whenAnonymous_throwsNotAuthenticatedWithoutLinking() = runTest {
        val (sut, authCommandProvider, _) = makeSUT(isAnonymous = true)

        try {
            sut(makeCredential())
            fail("Expected AuthError.NotAuthenticated to be thrown")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertEquals("an anonymous user goes through the merge path, never through this one", 0, authCommandProvider.linkCallCount)
    }

    @Test
    fun invoke_whenMergeSnapshotIsPending_throwsMergeInProgressWithoutLinking() = runTest {
        val (sut, authCommandProvider, snapshotStore) = makeSUT()
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = emptyList())

        try {
            sut(makeCredential())
            fail("Expected LinkCredentialError.MergeInProgress to be thrown")
        } catch (_: LinkCredentialError.MergeInProgress) {
        }

        assertEquals("an unfinished merge must finish first, or data lands under the wrong uid", 0, authCommandProvider.linkCallCount)
    }

    @Test
    fun invoke_whenProviderAlreadyLinked_throwsAlreadyLinked() = runTest {
        assertLinkFails(FirebaseAuthException("ERROR_PROVIDER_ALREADY_LINKED", "linked"), LinkCredentialError.AlreadyLinked)
    }

    @Test
    fun invoke_whenCredentialAlreadyInUse_throwsCredentialBelongsToAnotherAccount() = runTest {
        assertLinkFails(
            FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "in use"),
            LinkCredentialError.CredentialBelongsToAnotherAccount,
        )
    }

    @Test
    fun invoke_whenEmailAlreadyInUse_throwsEmailBelongsToAnotherAccount() = runTest {
        assertLinkFails(
            FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "in use"),
            LinkCredentialError.EmailBelongsToAnotherAccount,
        )
    }

    @Test
    fun invoke_whenLinkFailsWithOtherError_propagatesIt() = runTest {
        val (sut, authCommandProvider, _) = makeSUT()
        authCommandProvider.linkError = RuntimeException("offline")

        try {
            sut(makeCredential())
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }
    }

    // MARK: - Helpers

    private suspend fun assertLinkFails(error: Exception, expected: LinkCredentialError) {
        val (sut, authCommandProvider, _) = makeSUT()
        authCommandProvider.linkError = error

        try {
            sut(makeCredential())
            fail("Expected $expected to be thrown")
        } catch (thrown: LinkCredentialError) {
            assertEquals(expected, thrown)
        }
    }

    private fun makeSUT(isAnonymous: Boolean = false): Triple<LinkCredentialUseCase, AuthCommandProviderFake, PendingMergeSnapshotStoreFake> {
        val authCommandProvider = AuthCommandProviderFake()
        val snapshotStore = PendingMergeSnapshotStoreFake()
        val sut = LinkCredentialUseCase(
            authProvider = AuthProviderFake(isAnonymous = isAnonymous),
            authCommandProvider = authCommandProvider,
            snapshotStore = snapshotStore,
        )
        return Triple(sut, authCommandProvider, snapshotStore)
    }

    private fun makeCredential(): AuthCredential = GoogleAuthProvider.getCredential("id-token", null)
}
