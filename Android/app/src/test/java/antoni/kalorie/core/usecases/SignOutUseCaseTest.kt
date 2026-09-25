package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.GoogleSessionProviderFake
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SignOutUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_callsSignOutOnAuthCommandProvider() = runTest {
        val fixture = makeSUT()

        fixture.sut()

        assertEquals(1, fixture.authCommandProvider.signOutCallCount)
    }

    @Test
    fun invoke_whenSignOutFails_throwsError() = runTest {
        val fixture = makeSUT()
        fixture.authCommandProvider.signOutError = RuntimeException("unknown")

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }
    }

    @Test
    fun invoke_discardsPendingMergeSnapshot() = runTest {
        val fixture = makeSUT()
        fixture.snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = emptyList())

        fixture.sut()

        assertNull(fixture.snapshotStore.stubbedSnapshot)
    }

    @Test
    fun invoke_discardsSnapshotBeforeSigningOut() = runTest {
        val fixture = makeSUT()
        fixture.snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId = "anon-1", foodConsumed = emptyList())
        fixture.authCommandProvider.signOutError = RuntimeException("unknown")

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertNull(fixture.snapshotStore.stubbedSnapshot)
    }

    @Test
    fun invoke_clearsGoogleSession() = runTest {
        val fixture = makeSUT()

        fixture.sut()

        assertEquals(1, fixture.googleSessionProvider.clearSessionCallCount)
    }

    @Test
    fun invoke_whenSignOutFails_doesNotClearGoogleSession() = runTest {
        val fixture = makeSUT()
        fixture.authCommandProvider.signOutError = RuntimeException("unknown")

        try {
            fixture.sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }

        assertEquals(0, fixture.googleSessionProvider.clearSessionCallCount)
    }

    // MARK: - Helpers

    private class Fixture(
        val sut: SignOutUseCase,
        val authCommandProvider: AuthCommandProviderFake,
        val snapshotStore: PendingMergeSnapshotStoreFake,
        val googleSessionProvider: GoogleSessionProviderFake,
    )

    private fun makeSUT(): Fixture {
        val authCommandProvider = AuthCommandProviderFake()
        val snapshotStore = PendingMergeSnapshotStoreFake()
        val googleSessionProvider = GoogleSessionProviderFake()
        return Fixture(SignOutUseCase(authCommandProvider, snapshotStore, googleSessionProvider), authCommandProvider, snapshotStore, googleSessionProvider)
    }
}
