package antoni.kalorie.features.notifications

import antoni.kalorie.R
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.makeNotification
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseFake
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseFake
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseFake
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCaseProtocol
import antoni.kalorie.core.usecases.HideUserNotificationUseCaseProtocol
import antoni.kalorie.core.usecases.MarkUserNotificationSeenUseCaseProtocol
import antoni.kalorie.core.usecases.WithdrawUserNotificationError
import antoni.kalorie.core.usecases.WithdrawUserNotificationUseCaseProtocol
import com.google.firebase.firestore.FirebaseFirestoreException
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
import java.time.Instant

class NotificationsViewModelUserNotificationsTest {

    // MARK: - Tests

    @Test
    fun onAppear_whenMaintainer_doesNotFetchUserNotifications() = runTest {
        val spy = FetchUserNotificationsCounter()
        val sut = NotificationsViewModel(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(),
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
            isMaintainer = true,
            fetchUserNotifications = spy,
            markUserNotificationSeen = MarkSeenRecordingFake(),
            hideUserNotification = HideRecordingFake(),
            withdrawUserNotification = WithdrawRecordingFake(),
            notificationsStore = UserNotificationsStoreFake(),
        )

        sut.onAppear()

        assertEquals(0, spy.callCount)
    }

    @Test
    fun onAppear_listsTheFetchedNotifications() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222", UserNotificationDomain.Kind.PHOTO_SUGGESTION)))

        setup.sut.onAppear()

        assertEquals(listOf("foodItemReports/t111", "photoSuggestions/t222"), setup.sut.rows.value.map { it.id })
    }

    @Test
    fun onAppear_withoutAResolvedName_fallsBackToTheItemId() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111")))

        setup.sut.onAppear()

        assertEquals("111", setup.sut.rows.value.first().title)
    }

    @Test
    fun onAppear_whenTheFetchFails_showsTheAlertAndKeepsTheStoreUntouched() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = true)
        val setup = makeSUT(fetchFails = true, store = store)

        setup.sut.onAppear()

        assertNotNull(setup.sut.alertItem.value)
        assertTrue(store.mayHaveUnread)
        assertNull(store.lastCheckedAt)
    }

    @Test
    fun onAppear_afterASuccessfulFetch_updatesTheStore() = runTest {
        val store = UserNotificationsStoreFake(mayHaveUnread = false)
        val setup = makeSUT(notifications = listOf(unread("111")), store = store)

        setup.sut.onAppear()

        assertTrue(store.mayHaveUnread)
        assertNotNull(store.lastCheckedAt)
    }

    @Test
    fun onDetailShown_forAnUnreadRow_marksItOnceAndClearsItsDot() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222")))
        setup.sut.onAppear()

        setup.sut.onDetailShown("foodItemReports/t111")

        assertEquals(listOf("111"), setup.mark.marked.map { it.itemId })
        assertEquals(listOf(false, true), setup.sut.rows.value.map { it.isUnread })
    }

    @Test
    fun onDetailShown_again_doesNotMarkASecondTimeWhileTheRowIsRead() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111")))
        setup.sut.onAppear()

        setup.sut.onDetailShown("foodItemReports/t111")
        setup.sut.onDetailShown("foodItemReports/t111")

        assertEquals("once the row is read locally, another opening costs no write", 1, setup.mark.marked.size)
    }

    @Test
    fun onDetailShown_forAnAlreadyReadRow_marksNothing() = runTest {
        val created = Instant.now().minusSeconds(60)
        val setup = makeSUT(notifications = listOf(makeNotification(id = "t111", itemId = "111", createdAt = created, seenAt = Instant.now())))
        setup.sut.onAppear()

        setup.sut.onDetailShown("foodItemReports/t111")

        assertTrue(setup.mark.marked.isEmpty())
    }

    @Test
    fun onDetailShown_whenMarkingFails_leavesTheRowUnreadWithoutAnAlert() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111")), markFails = true)
        setup.sut.onAppear()

        setup.sut.onDetailShown("foodItemReports/t111")

        assertTrue("the next opening must try again", setup.sut.rows.value.first().isUnread)
        assertNull(setup.sut.alertItem.value)
    }

    @Test
    fun onDetailShown_whenTheWriteFailsForAnotherFirestoreReason_leavesTheRowUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), markFails = true, markError = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE), store = store)
        setup.sut.onAppear()

        setup.sut.onDetailShown("foodItemReports/t111")

        assertTrue(setup.sut.rows.value.first().isUnread)
        assertTrue(store.mayHaveUnread)
    }

    @Test
    fun onDetailShown_forTheLastUnreadRow_clearsMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222")), store = store)
        setup.sut.onAppear()
        assertTrue(store.mayHaveUnread)

        setup.sut.onDetailShown("foodItemReports/t111")
        assertTrue(store.mayHaveUnread)
        setup.sut.onDetailShown("foodItemReports/t222")

        assertFalse("otherwise the bell keeps querying until the daily check finds nothing unread", store.mayHaveUnread)
    }

    @Test
    fun onDetailShown_whenMarkingFails_doesNotClearMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), markFails = true, store = store)
        setup.sut.onAppear()

        setup.sut.onDetailShown("foodItemReports/t111")

        assertTrue(store.mayHaveUnread)
    }

    @Test
    fun onDetailShown_whenTheScreenIsLeftDuringTheWrite_stillMarksTheRowSeen() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), store = store, markGate = gate)
        setup.sut.onAppear()

        val job = launch { setup.sut.onDetailShown("foodItemReports/t111") }
        yield()
        job.cancel()
        gate.complete(Unit)
        job.join()

        assertNotNull("the write reached the server, so the dot must go", seenAt(setup.sut.rows.value.first()))
        assertFalse(store.mayHaveUnread)
    }

    @Test
    fun onWithdrawConfirmed_removesTheRowAndReportsWithdrawn() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222")))
        setup.sut.onAppear()

        val result = setup.sut.onWithdrawConfirmed("foodItemReports/t111")

        assertEquals(WithdrawResult.WITHDRAWN, result)
        assertEquals(listOf("foodItemReports/t222"), setup.sut.rows.value.map { it.id })
        assertEquals(listOf("111"), setup.withdraw.withdrawn.map { it.itemId })
    }

    @Test
    fun onWithdrawConfirmed_whenItWasTheLastUnreadRow_clearsMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), store = store)
        setup.sut.onAppear()
        assertTrue(store.mayHaveUnread)

        setup.sut.onWithdrawConfirmed("foodItemReports/t111")

        assertFalse("a withdrawn notification can no longer be unread", store.mayHaveUnread)
    }

    @Test
    fun onWithdrawConfirmed_whenAnotherRowIsStillUnread_keepsMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222")), store = store)
        setup.sut.onAppear()

        setup.sut.onWithdrawConfirmed("foodItemReports/t111")

        assertTrue(store.mayHaveUnread)
    }

    @Test
    fun onWithdrawConfirmed_whenItFails_keepsTheRowAndReportsFailed() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), withdrawFails = true, store = store)
        setup.sut.onAppear()

        val result = setup.sut.onWithdrawConfirmed("foodItemReports/t111")

        assertEquals(WithdrawResult.FAILED, result)
        assertEquals("the row stays so the user can retry", listOf("foodItemReports/t111"), setup.sut.rows.value.map { it.id })
        assertTrue(store.mayHaveUnread)
    }

    @Test
    fun onWithdrawConfirmed_whenTheDeleteFailsForAnotherFirestoreReason_keepsTheRowAndReportsFailed() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), withdrawFails = true, withdrawError = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE), store = store)
        setup.sut.onAppear()

        val result = setup.sut.onWithdrawConfirmed("foodItemReports/t111")

        assertEquals(WithdrawResult.FAILED, result)
        assertEquals(listOf("foodItemReports/t111"), setup.sut.rows.value.map { it.id })
        assertTrue(store.mayHaveUnread)
    }

    @Test
    fun onWithdrawConfirmed_whenTheScreenIsLeftDuringTheDelete_stillRemovesTheRow() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), store = store, withdrawGate = gate)
        setup.sut.onAppear()

        val job = launch { setup.sut.onWithdrawConfirmed("foodItemReports/t111") }
        yield()
        job.cancel()
        gate.complete(Unit)
        job.join()

        assertTrue("the delete reached the server, so the row must not linger", setup.sut.rows.value.isEmpty())
        assertFalse(store.mayHaveUnread)
    }

    @Test
    fun onWithdrawConfirmed_forAnUnknownRow_reportsFailed() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111")))
        setup.sut.onAppear()

        val result = setup.sut.onWithdrawConfirmed("foodItemReports/t999")

        assertEquals(WithdrawResult.FAILED, result)
        assertTrue(setup.withdraw.withdrawn.isEmpty())
    }

    @Test
    fun onWithdrawConfirmed_whenTheMaintainerResolvedItMeanwhile_reloadsTheListAndReportsAlreadyResolved() = runTest {
        val setup = makeSUT(
            notifications = listOf(unread("111")),
            withdrawFails = true,
            withdrawError = WithdrawUserNotificationError.AlreadyResolved,
        )
        setup.sut.onAppear()

        val result = setup.sut.onWithdrawConfirmed("foodItemReports/t111")

        assertEquals(WithdrawResult.ALREADY_RESOLVED, result)
        assertEquals("the user must see the outcome the maintainer wrote, not a stale pending row", 2, setup.fetch.callCount)
    }

    @Test
    fun onHideTapped_removesTheRowAtOnceAndHidesTheTicket() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222")))
        setup.sut.onAppear()

        setup.sut.onHideTapped("foodItemReports/t111")

        assertEquals(listOf("foodItemReports/t222"), setup.sut.rows.value.map { it.id })
        assertEquals(listOf("t111"), setup.hide.hidden.map { it.id })
    }

    @Test
    fun onHideTapped_whenItFails_restoresTheRowInPlaceAndAlerts() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111"), unread("222")), hideFails = true)
        setup.sut.onAppear()

        setup.sut.onHideTapped("foodItemReports/t111")

        assertEquals(
            "a row that was not hidden server-side must not disappear",
            listOf("foodItemReports/t111", "foodItemReports/t222"),
            setup.sut.rows.value.map { it.id },
        )
        assertEquals(R.string.common_error_unknown, setup.sut.alertItem.value?.titleRes)
    }

    @Test
    fun onHideTapped_whenTheLastUnreadRowIsHidden_clearsMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val setup = makeSUT(notifications = listOf(unread("111")), store = store)
        setup.sut.onAppear()
        assertTrue(store.mayHaveUnread)

        setup.sut.onHideTapped("foodItemReports/t111")

        assertFalse("a hidden notification can no longer light the bell", store.mayHaveUnread)
    }

    @Test
    fun onHideTapped_forAnUnknownRow_changesNothing() = runTest {
        val setup = makeSUT(notifications = listOf(unread("111")))
        setup.sut.onAppear()

        setup.sut.onHideTapped("foodItemReports/t999")

        assertEquals(listOf("foodItemReports/t111"), setup.sut.rows.value.map { it.id })
        assertTrue(setup.hide.hidden.isEmpty())
    }

    // MARK: - Helpers

    private class Setup(
        val sut: NotificationsViewModel,
        val mark: MarkSeenRecordingFake,
        val withdraw: WithdrawRecordingFake,
        val hide: HideRecordingFake,
        val fetch: FetchUserNotificationsCounter,
    )

    private fun unread(itemId: String, kind: UserNotificationDomain.Kind = UserNotificationDomain.Kind.REPORT) = makeNotification(kind = kind, id = "t$itemId", itemId = itemId, createdAt = Instant.now())

    private fun seenAt(row: NotificationRow) = (row.kind as? NotificationRow.Kind.UserNotification)?.notification?.seenAt

    private fun firestoreError(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException(code.name, code)

    private fun makeSUT(
        notifications: List<UserNotificationDomain> = emptyList(),
        fetchFails: Boolean = false,
        markFails: Boolean = false,
        markError: Exception = RuntimeException("unknown"),
        markGate: CompletableDeferred<Unit>? = null,
        withdrawFails: Boolean = false,
        withdrawError: Exception = RuntimeException("unknown"),
        withdrawGate: CompletableDeferred<Unit>? = null,
        hideFails: Boolean = false,
        store: UserNotificationsStoreFake = UserNotificationsStoreFake(),
    ): Setup {
        val mark = MarkSeenRecordingFake(markFails, markError, markGate)
        val withdraw = WithdrawRecordingFake(withdrawFails, withdrawError, withdrawGate)
        val hide = HideRecordingFake(hideFails)
        val fetch = FetchUserNotificationsCounter(notifications, fetchFails)
        val sut = NotificationsViewModel(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(),
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
            isMaintainer = false,
            fetchUserNotifications = fetch,
            markUserNotificationSeen = mark,
            hideUserNotification = hide,
            withdrawUserNotification = withdraw,
            notificationsStore = store,
        )
        return Setup(sut, mark, withdraw, hide, fetch)
    }
}

private class WithdrawRecordingFake(
    private val shouldThrow: Boolean = false,
    private val error: Exception = RuntimeException("unknown"),
    private val gate: CompletableDeferred<Unit>? = null,
) : WithdrawUserNotificationUseCaseProtocol {
    val withdrawn = mutableListOf<UserNotificationDomain>()

    override suspend fun invoke(notification: UserNotificationDomain) {
        gate?.await()
        if (shouldThrow) throw error
        withdrawn.add(notification)
    }
}

private class MarkSeenRecordingFake(
    private val shouldThrow: Boolean = false,
    private val error: Exception = RuntimeException("unknown"),
    private val gate: CompletableDeferred<Unit>? = null,
) : MarkUserNotificationSeenUseCaseProtocol {
    val marked = mutableListOf<UserNotificationDomain>()

    override suspend fun invoke(notification: UserNotificationDomain) {
        gate?.await()
        if (shouldThrow) throw error
        marked.add(notification)
    }
}

private class HideRecordingFake(
    private val shouldThrow: Boolean = false,
) : HideUserNotificationUseCaseProtocol {
    val hidden = mutableListOf<UserNotificationDomain>()

    override suspend fun invoke(notification: UserNotificationDomain) {
        if (shouldThrow) throw RuntimeException("unknown")
        hidden.add(notification)
    }
}

private class FetchUserNotificationsCounter(
    private val notifications: List<UserNotificationDomain> = emptyList(),
    private val shouldThrow: Boolean = false,
) : FetchUserNotificationsUseCaseProtocol {
    var callCount = 0

    override suspend fun invoke(): List<UserNotificationDomain> {
        callCount += 1
        if (shouldThrow) throw RuntimeException("unknown")
        return notifications
    }
}
