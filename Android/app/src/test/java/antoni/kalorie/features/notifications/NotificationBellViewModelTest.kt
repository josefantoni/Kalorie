package antoni.kalorie.features.notifications

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import kotlinx.coroutines.test.runTest
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
    override suspend fun invoke(): Boolean = result
}
