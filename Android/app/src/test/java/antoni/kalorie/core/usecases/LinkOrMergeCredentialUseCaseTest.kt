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

    // MARK: - Helpers

    private fun makeSUT(anonymousUserId: String? = "anon-123"): Triple<LinkOrMergeCredentialUseCase, AuthCommandProviderFake, MigrateAnonymousDataUseCaseFake> {
        val authCommandProvider = AuthCommandProviderFake()
        val migrateAnonymousData = MigrateAnonymousDataUseCaseFake()
        val sut = LinkOrMergeCredentialUseCase(
            authProvider = AuthProviderFake(userId = anonymousUserId),
            authCommandProvider = authCommandProvider,
            migrateAnonymousData = migrateAnonymousData,
        )
        return Triple(sut, authCommandProvider, migrateAnonymousData)
    }

    private fun makeCredential(): AuthCredential = GoogleAuthProvider.getCredential("id-token", null)
}
