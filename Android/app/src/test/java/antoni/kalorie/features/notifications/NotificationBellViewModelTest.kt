package antoni.kalorie.features.notifications

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.makeNotification
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCaseFake
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCaseProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationBellViewModelTest {

    // MARK: - Tests

    @Test
    fun onAppear_whenMaintainer_usesThePendingCountsForTheDot() = runTest {
        val (sut, _, _) = makeSUT(claim = Result.success(true), hasPending = true)

        sut.onAppear()

        assertTrue(sut.isMaintainer.value)
        assertTrue(sut.hasPendingNotifications.value)
    }

    @Test
    fun onForeground_afterAFailedClaimLookup_recognisesTheMaintainerAgain() = runTest {
        val (sut, claim, _) = makeSUT(claim = Result.failure(AuthError.NotAuthenticated), hasPending = true)

        sut.onAppear()
        assertFalse(sut.isMaintainer.value)
        claim.result = Result.success(true)
        sut.onForeground()

        assertTrue("one failed lookup must not demote the maintainer for the rest of the session", sut.isMaintainer.value)
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
        val sut = NotificationBellViewModel(claim, pending, FetchUserNotificationsUseCaseFake(), UserNotificationsStoreFake())

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

    @Test
    fun onBellTapped_beforeTheMaintainerClaimResolves_doesNotOpenTheSheet() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sut = NotificationBellViewModel(GatedClaimFake(gate, true), FetchHasPendingFake(true), FetchUserNotificationsUseCaseFake(), UserNotificationsStoreFake())

        val appear = launch { sut.onAppear() }
        yield()
        sut.onBellTapped()
        assertFalse("a maintainer tapping during the claim lookup would get the user list for the whole sheet", sut.isSheetVisible.value)
        gate.complete(Unit)
        appear.join()
        sut.onBellTapped()

        assertTrue(sut.isSheetVisible.value)
        assertTrue(sut.isMaintainer.value)
    }

    @Test
    fun onBellTapped_afterAFailedClaimLookup_opensTheSheetForTheUser() = runTest {
        val (sut, _, _) = makeSUT(claim = Result.failure(AuthError.NotAuthenticated), hasPending = false)

        sut.onAppear()
        sut.onBellTapped()

        assertTrue(sut.isSheetVisible.value)
        assertFalse(sut.isMaintainer.value)
    }

    @Test
    fun onRefresh_whileAGatedRefreshIsInFlight_stillQueriesTheUserNotifications() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = Instant.now().minusSeconds(3_600))
        val user = FetchUserNotificationsCountingFake(listOf(unread()), false)
        val sut = NotificationBellViewModel(GatedClaimFake(gate, false), FetchHasPendingFake(false), user, store)

        val foreground = launch { sut.onForeground() }
        yield()
        val refresh = launch { sut.onRefresh() }
        yield()
        gate.complete(Unit)
        foreground.join()
        refresh.join()

        assertEquals("a pull right after the resume refresh must not stay behind the daily gate", 1, user.callCount)
        assertTrue(sut.hasPendingNotifications.value)
    }

    @Test
    fun onRefresh_finishingWhileAnotherRefreshIsInFlight_doesNotLetTheNextForegroundDuplicateIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val claim = FirstCallGatedClaimFake(gate)
        val sut = NotificationBellViewModel(claim, FetchHasPendingFake(false), FetchUserNotificationsUseCaseFake(), UserNotificationsStoreFake())

        val foreground = launch { sut.onForeground() }
        yield()
        sut.onRefresh()
        sut.onForeground()
        gate.complete(Unit)
        foreground.join()

        assertEquals("a pull that ends first must not clear the flag the in-flight refresh still owns", 2, claim.callCount)
    }

    @Test
    fun onForeground_whenTheLastCheckIsInTheFuture_queriesAndRestampsTheCheck() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = Instant.now().plusSeconds(3 * 24 * 3_600))
        val setup = makeFull(store = store)

        setup.sut.onForeground()

        assertEquals("a clock that was ahead during a fetch must not keep the daily check closed", 1, setup.user.callCount)
        val lastCheckedAt = store.lastCheckedAt ?: Instant.MAX
        assertTrue(Duration.between(lastCheckedAt, Instant.now()).abs().seconds < 60)
    }

    @Test
    fun onAppear_whenMaintainer_neverCallsTheUserFetch() = runTest {
        val setup = makeFull(claim = Result.success(true), hasPending = true)

        setup.sut.onAppear()

        assertEquals(0, setup.user.callCount)
    }

    @Test
    fun onAppear_whenClaimLookupFails_treatsTheUserAsNonMaintainer() = runTest {
        val setup = makeFull(claim = Result.failure(AuthError.NotAuthenticated), hasPending = true, unread = listOf(unread()))

        setup.sut.onAppear()

        assertFalse(setup.sut.isMaintainer.value)
        assertTrue(setup.sut.hasPendingNotifications.value)
        assertEquals(1, setup.user.callCount)
    }

    @Test
    fun onAppear_nonMaintainerWithAnUnreadNotification_showsTheDot() = runTest {
        val setup = makeFull(unread = listOf(unread()), store = UserNotificationsStoreFake(mayHaveUnread = true))

        setup.sut.onAppear()

        assertTrue(setup.sut.hasPendingNotifications.value)
    }

    @Test
    fun onAppear_nonMaintainerWithEverythingRead_showsNoDot() = runTest {
        val setup = makeFull(unread = listOf(read()))

        setup.sut.onAppear()

        assertFalse(setup.sut.hasPendingNotifications.value)
    }

    @Test
    fun onAppear_whenTheUserFetchFails_showsNoDot() = runTest {
        val setup = makeFull(userFetchFails = true)

        setup.sut.onAppear()

        assertFalse(setup.sut.hasPendingNotifications.value)
    }

    @Test
    fun onAppear_whenNothingMayBeUnreadAndTheLastCheckIsFresh_doesNotQuery() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = Instant.now().minusSeconds(3_600))
        val setup = makeFull(unread = listOf(unread()), store = store)

        setup.sut.onAppear()

        assertEquals("two reads on every Dashboard appearance for every user is the cost the gate exists to avoid", 0, setup.user.callCount)
        assertFalse(setup.sut.hasPendingNotifications.value)
    }

    @Test
    fun onAppear_whenAnUnreadNotificationIsPossible_queries() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = true, lastCheckedAt = Instant.now())
        val setup = makeFull(unread = listOf(unread()), store = store)

        setup.sut.onAppear()

        assertEquals(1, setup.user.callCount)
    }

    @Test
    fun onAppear_withNoPreviousCheck_queries() = runTest {
        val setup = makeFull(store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = null))

        setup.sut.onAppear()

        assertEquals(1, setup.user.callCount)
    }

    @Test
    fun onAppear_whenTheLastCheckIsOlderThanADay_queries() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = Instant.now().minusSeconds(25 * 3_600))
        val setup = makeFull(store = store)

        setup.sut.onAppear()

        assertEquals("this daily check is how a report sent on another device reaches this one", 1, setup.user.callCount)
    }

    @Test
    fun onAppear_afterASuccessfulFetchWithNothingUnread_clearsTheFlagAndStampsTheCheck() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = true, lastCheckedAt = null)
        val setup = makeFull(unread = listOf(read()), store = store)

        setup.sut.onAppear()

        assertFalse(store.mayHaveUnread)
        assertNotNull(store.lastCheckedAt)
    }

    @Test
    fun onAppear_afterAFetchWithAnUnreadNotification_keepsTheFlag() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = null)
        val setup = makeFull(unread = listOf(unread()), store = store)

        setup.sut.onAppear()

        assertTrue(store.mayHaveUnread)
        assertNotNull(store.lastCheckedAt)
    }

    @Test
    fun onAppear_afterAFailedFetch_changesNeitherTheFlagNorTheTimestamp() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = true, lastCheckedAt = null)
        val setup = makeFull(userFetchFails = true, store = store)

        setup.sut.onAppear()

        assertTrue(store.mayHaveUnread)
        assertNull(store.lastCheckedAt)
    }

    @Test
    fun onRefresh_queriesEvenWhenTheGateIsClosed() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = Instant.now())
        val setup = makeFull(unread = listOf(unread()), store = store)

        setup.sut.onRefresh()

        assertEquals("pull to refresh is the user's way to pull in a notification from another device", 1, setup.user.callCount)
        assertTrue(setup.sut.hasPendingNotifications.value)
    }

    @Test
    fun onDotRefreshNeeded_respectsTheGate() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = Instant.now())
        val setup = makeFull(unread = listOf(unread()), store = store)

        setup.sut.onAppear()
        setup.sut.onDotRefreshNeeded()

        assertEquals(0, setup.user.callCount)
    }

    @Test
    fun onAppear_whenASubmitLandsDuringTheFetch_keepsTheFlagAndDoesNotStampTheCheck() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = UserNotificationsStoreFake(mayHaveUnread = false, lastCheckedAt = null)
        val sut = NotificationBellViewModel(FetchMaintainerClaimFake(Result.success(false)), FetchHasPendingFake(false), GatedUserFetchFake(gate), store)

        val appear = launch { sut.onAppear() }
        yield()
        store.mayHaveUnread = true
        store.lastSubmittedAt = Instant.now()
        gate.complete(Unit)
        appear.join()

        assertTrue("the fetch started before the submit and cannot have seen the new document", store.mayHaveUnread)
        assertNull("stamping the check would close the gate for a day", store.lastCheckedAt)
        assertTrue(sut.hasPendingNotifications.value)
    }

    @Test
    fun recordFetch_whenSubmittedAfterTheFetchStarted_changesNothing() {
        val store = UserNotificationsStoreFake(mayHaveUnread = true, lastCheckedAt = null, lastSubmittedAt = Instant.now())

        store.recordFetch(emptyList(), Instant.now().minusSeconds(1))

        assertTrue(store.mayHaveUnread)
        assertNull(store.lastCheckedAt)
    }

    @Test
    fun recordFetch_whenSubmittedBeforeTheFetchStarted_recordsTheResult() {
        val store = UserNotificationsStoreFake(mayHaveUnread = true, lastCheckedAt = null, lastSubmittedAt = Instant.now().minusSeconds(10))

        store.recordFetch(emptyList(), Instant.now())

        assertFalse(store.mayHaveUnread)
        assertNotNull(store.lastCheckedAt)
    }

    @Test
    fun onRefresh_whenAGatedRefreshFinishesFirst_stillGuardsTheDotAgainstTheNextForeground() = runTest {
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        val user = FirstTwoGatedUserFetchesFake(gates, listOf(unread()))
        val sut = NotificationBellViewModel(FetchMaintainerClaimFake(Result.success(false)), FetchHasPendingFake(false), user, UserNotificationsStoreFake(mayHaveUnread = true))

        val appear = launch { sut.onAppear() }
        yield()
        val refresh = launch { sut.onRefresh() }
        yield()
        gates[0].complete(Unit)
        appear.join()
        sut.onSheetDismissed()

        assertEquals("the pull's fetch is still in flight, so the dismissal must not start a third billed query", 2, user.callCount)

        gates[1].complete(Unit)
        refresh.join()
    }

    @Test
    fun onSheetDismissed_whileARefreshIsInFlight_recomputesTheMaintainersDotAfterwards() = runTest {
        val gate = CompletableDeferred<Unit>()
        val pending = FirstCallGatedPendingFake(gate)
        val sut = NotificationBellViewModel(FetchMaintainerClaimFake(Result.success(true)), pending, FetchUserNotificationsUseCaseFake(), UserNotificationsStoreFake())

        val foreground = launch { sut.onForeground() }
        yield()
        sut.onSheetDismissed()
        gate.complete(Unit)
        foreground.join()

        assertFalse("the count taken before the queue was resolved must not leave the dot on", sut.hasPendingNotifications.value)
        assertEquals(2, pending.callCount)
    }

    @Test
    fun recordFetch_whenSubmittedInTheFuture_recordsTheResult() {
        val store = UserNotificationsStoreFake(mayHaveUnread = true, lastCheckedAt = null, lastSubmittedAt = Instant.now().plusSeconds(3_600))

        store.recordFetch(emptyList(), Instant.now())

        assertFalse("a clock set back after a submit must not drop every later fetch", store.mayHaveUnread)
        assertNotNull(store.lastCheckedAt)
    }

    @Test
    fun onDotRefreshNeededAndOnSheetDismissed_beforeTheClaimResolves_doNotTakeTheUserBranch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val user = FetchUserNotificationsCountingFake(listOf(unread()), false)
        val store = UserNotificationsStoreFake(mayHaveUnread = true)
        val sut = NotificationBellViewModel(GatedClaimFake(gate, true), FetchHasPendingFake(true), user, store)

        val appear = launch { sut.onAppear() }
        yield()
        sut.onDotRefreshNeeded()
        sut.onSheetDismissed()

        assertEquals("a maintainer must never run the user queries", 0, user.callCount)
        assertNull(store.lastCheckedAt)

        gate.complete(Unit)
        appear.join()

        assertTrue(sut.isMaintainer.value)
        assertTrue(sut.hasPendingNotifications.value)
        assertEquals(0, user.callCount)
    }

    // MARK: - Helpers

    private class Full(val sut: NotificationBellViewModel, val user: FetchUserNotificationsCountingFake)

    private fun unread() = makeNotification(itemId = "111", createdAt = Instant.now())

    private fun read() = makeNotification(itemId = "111", createdAt = Instant.now().minusSeconds(60), seenAt = Instant.now())

    private fun makeFull(
        claim: Result<Boolean> = Result.success(false),
        hasPending: Boolean = false,
        unread: List<UserNotificationDomain> = emptyList(),
        userFetchFails: Boolean = false,
        store: UserNotificationsStoreFake = UserNotificationsStoreFake(),
    ): Full {
        val user = FetchUserNotificationsCountingFake(unread, userFetchFails)
        return Full(NotificationBellViewModel(FetchMaintainerClaimFake(claim), FetchHasPendingFake(hasPending), user, store), user)
    }

    private fun makeSUT(claim: Result<Boolean>, hasPending: Boolean): Triple<NotificationBellViewModel, FetchMaintainerClaimFake, FetchHasPendingFake> {
        val claimFake = FetchMaintainerClaimFake(claim)
        val pendingFake = FetchHasPendingFake(hasPending)
        return Triple(NotificationBellViewModel(claimFake, pendingFake, FetchUserNotificationsUseCaseFake(), UserNotificationsStoreFake()), claimFake, pendingFake)
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

private class GatedClaimFake(
    private val gate: CompletableDeferred<Unit>,
    private val result: Boolean = true,
) : FetchMaintainerClaimUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): Boolean {
        callCount += 1
        gate.await()
        return result
    }
}

private class FirstCallGatedClaimFake(private val gate: CompletableDeferred<Unit>) : FetchMaintainerClaimUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): Boolean {
        callCount += 1
        if (callCount == 1) gate.await()
        return false
    }
}

private class FetchUserNotificationsCountingFake(
    private val notifications: List<UserNotificationDomain>,
    private val shouldThrow: Boolean,
) : FetchUserNotificationsUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): List<UserNotificationDomain> {
        callCount += 1
        if (shouldThrow) throw RuntimeException("unknown")
        return notifications
    }
}

private class GatedUserFetchFake(private val gate: CompletableDeferred<Unit>) : FetchUserNotificationsUseCaseProtocol {
    override suspend fun invoke(): List<UserNotificationDomain> {
        gate.await()
        return emptyList()
    }
}

private class FirstTwoGatedUserFetchesFake(
    private val gates: List<CompletableDeferred<Unit>>,
    private val notifications: List<UserNotificationDomain>,
) : FetchUserNotificationsUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): List<UserNotificationDomain> {
        callCount += 1
        if (callCount <= gates.size) gates[callCount - 1].await()
        return notifications
    }
}

private class FirstCallGatedPendingFake(private val gate: CompletableDeferred<Unit>) : FetchHasPendingNotificationsUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): Boolean {
        callCount += 1
        if (callCount > 1) return false
        gate.await()
        return true
    }
}
