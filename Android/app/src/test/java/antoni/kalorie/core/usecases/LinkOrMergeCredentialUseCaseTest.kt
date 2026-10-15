package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthProviderFake
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LinkOrMergeCredentialUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_whenLinkSucceeds_doesNotCallMigrate() = runTest {
        val (sut, authCommandProvider, migrateAnonymousData) = makeSUT()

        sut(makeCredential())

        assertEquals(1, authCommandProvider.linkCallCount)
        assertEquals(0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun invoke_whenCredentialAlreadyInUse_callsMigrateWithAnonymousUserId() = runTest {
        val (sut, authCommandProvider, migrateAnonymousData) = makeSUT(anonymousUserId = "anon-123")
        authCommandProvider.linkError = FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "in use")

        sut(makeCredential())

        assertEquals(1, migrateAnonymousData.migrateCallCount)
        assertEquals("anon-123", migrateAnonymousData.lastSourceUserId)
    }

    @Test
    fun invoke_whenLinkFailsWithOtherError_propagatesErrorAndDoesNotCallMigrate() = runTest {
        val (sut, authCommandProvider, migrateAnonymousData) = makeSUT()
        authCommandProvider.linkError = RuntimeException("offline")

        try {
            sut(makeCredential())
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertEquals(0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun invoke_whenEmailAlreadyInUse_throwsAccountExistsWithAnotherProvider() = runTest {
        val (sut, authCommandProvider, migrateAnonymousData) = makeSUT()
        authCommandProvider.linkError = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "in use")

        try {
            sut(makeCredential())
            fail("Expected error to be thrown")
        } catch (_: LinkOrMergeCredentialError.AccountExistsWithAnotherProvider) {
        }

        assertEquals("emailAlreadyInUse steers to the other provider, it does not merge", 0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun invoke_whenSignedIn_throwsNotAnonymousAndDoesNotLinkOrMigrate() = runTest {
        val (sut, authCommandProvider, migrateAnonymousData) = makeSUT(isAnonymous = false)
        authCommandProvider.linkError = FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "in use")

        try {
            sut(makeCredential())
            fail("Expected error to be thrown")
        } catch (_: LinkOrMergeCredentialError.NotAnonymous) {
        }

        assertEquals(0, authCommandProvider.linkCallCount)
        assertEquals("a signed-in user must never have their data merged into another account", 0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun linkOrMerge_whenLinkSucceeds_doesNotCallMigrate() = runTest {
        val (sut, _, migrateAnonymousData) = makeSUT()
        var linked = false

        sut.linkOrMerge { linked = true }

        assertEquals(true, linked)
        assertEquals(0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun linkOrMerge_whenCredentialAlreadyInUse_migratesWithTheCredentialFromTheException() = runTest {
        val (sut, _, migrateAnonymousData) = makeSUT(anonymousUserId = "anon-123")
        val updatedCredential = makeCredential()

        sut.linkOrMerge {
            // The SDK sets updatedCredential through an obfuscated public method; the constructor cannot take it.
            throw FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "in use").zza(updatedCredential)
        }

        assertEquals(1, migrateAnonymousData.migrateCallCount)
        assertEquals("anon-123", migrateAnonymousData.lastSourceUserId)
        assertEquals(updatedCredential, migrateAnonymousData.lastCredential)
    }

    @Test
    fun linkOrMerge_whenCollisionCarriesNoCredential_propagatesErrorAndDoesNotMigrate() = runTest {
        val (sut, _, migrateAnonymousData) = makeSUT()

        try {
            sut.linkOrMerge { throw FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "in use") }
            fail("Expected error to be thrown")
        } catch (_: FirebaseAuthUserCollisionException) {
        }

        assertEquals("without the credential there is nothing to sign in with, so the data must not be snapshotted", 0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun linkOrMerge_whenEmailAlreadyInUse_throwsAccountExistsWithAnotherProvider() = runTest {
        val (sut, _, migrateAnonymousData) = makeSUT()

        try {
            sut.linkOrMerge { throw FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "in use") }
            fail("Expected error to be thrown")
        } catch (_: LinkOrMergeCredentialError.AccountExistsWithAnotherProvider) {
        }

        assertEquals(0, migrateAnonymousData.migrateCallCount)
    }

    @Test
    fun linkOrMerge_whenSignedIn_throwsNotAnonymousWithoutLinking() = runTest {
        val (sut, _, migrateAnonymousData) = makeSUT(isAnonymous = false)
        var linked = false

        try {
            sut.linkOrMerge { linked = true }
            fail("Expected error to be thrown")
        } catch (_: LinkOrMergeCredentialError.NotAnonymous) {
        }

        assertEquals(false, linked)
        assertEquals(0, migrateAnonymousData.migrateCallCount)
    }

    // MARK: - Helpers

    private fun makeSUT(
        anonymousUserId: String? = "anon-123",
        isAnonymous: Boolean = true,
    ): Triple<LinkOrMergeCredentialUseCase, AuthCommandProviderFake, MigrateAnonymousDataUseCaseFake> {
        val authCommandProvider = AuthCommandProviderFake()
        val migrateAnonymousData = MigrateAnonymousDataUseCaseFake()
        val sut = LinkOrMergeCredentialUseCase(
            authProvider = AuthProviderFake(userId = anonymousUserId, isAnonymous = isAnonymous),
            authCommandProvider = authCommandProvider,
            migrateAnonymousData = migrateAnonymousData,
        )
        return Triple(sut, authCommandProvider, migrateAnonymousData)
    }

    private fun makeCredential(): AuthCredential = GoogleAuthProvider.getCredential("id-token", null)
}
