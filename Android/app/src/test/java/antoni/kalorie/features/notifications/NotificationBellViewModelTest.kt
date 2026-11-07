package antoni.kalorie.features.notifications

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBellViewModelTest {

    // MARK: - Tests

    @Test
    fun onAppear_whenMaintainer_showsTheBellAndTheDot() = runTest {
        val (sut, _, _) = makeSUT(claim = Result.success(true), hasPending = true)

        sut.onAppear()

        assertTrue(sut.isBellVisible.value)
        assertTrue(sut.hasPendingNotifications.value)
    }

    @Test
    fun onForeground_afterAFailedClaimLookup_showsTheBell() = runTest {
        val (sut, claim, _) = makeSUT(claim = Result.failure(AuthError.NotAuthenticated), hasPending = true)

        sut.onAppear()
        assertFalse(sut.isBellVisible.value)
        claim.result = Result.success(true)
        sut.onForeground()

        assertTrue("one failed lookup must not hide the bell for the rest of the session", sut.isBellVisible.value)
        assertTrue(sut.hasPendingNotifications.value)
    }

    @Test
    fun onSheetDismissed_recomputesTheDot() = runTest {
        val (sut, _, pending) = makeSUT(claim = Result.success(true), hasPending = true)
        sut.onAppear()
        assertTrue(sut.hasPendingNotifications.value)

        pending.result = false
        sut.onSheetDismissed()

        assertFalse(sut.hasPendingNotifications.value)
    }

    @Test
    fun onAppearAndOnForeground_whileARefreshIsInFlight_readTheClaimAndCountOnlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val claim = GatedClaimFake(gate)
        val pending = FetchHasPendingFake(true)
        val sut = NotificationBellViewModel(claim, pending)

        val first = launch { sut.onAppear() }
        yield()
        val second = launch { sut.onForeground() }
        yield()
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals("the entry composition fires both calls; the second must not repeat the claim read", 1, claim.callCount)
        assertEquals(1, pending.callCount)
    }

    // MARK: - Helpers

    private fun makeSUT(claim: Result<Boolean>, hasPending: Boolean): Triple<NotificationBellViewModel, FetchMaintainerClaimFake, FetchHasPendingFake> {
        val claimFake = FetchMaintainerClaimFake(claim)
        val pendingFake = FetchHasPendingFake(hasPending)
        return Triple(NotificationBellViewModel(claimFake, pendingFake), claimFake, pendingFake)
    }
}

private class FetchMaintainerClaimFake(var result: Result<Boolean>) : FetchMaintainerClaimUseCaseProtocol {
    override suspend fun invoke(): Boolean = result.getOrThrow()
}

private class FetchHasPendingFake(var result: Boolean) : FetchHasPendingNotificationsUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): Boolean {
        callCount += 1
        return result
    }
}

private class GatedClaimFake(private val gate: CompletableDeferred<Unit>) : FetchMaintainerClaimUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): Boolean {
        callCount += 1
        gate.await()
        return true
    }
}
