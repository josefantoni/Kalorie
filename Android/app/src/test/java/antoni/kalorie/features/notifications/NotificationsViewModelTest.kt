package antoni.kalorie.features.notifications

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseFake
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseFake
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCaseFake
import antoni.kalorie.core.usecases.HideUserNotificationUseCaseFake
import antoni.kalorie.core.usecases.MarkUserNotificationSeenUseCaseFake
import antoni.kalorie.core.usecases.WithdrawUserNotificationUseCaseFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationsViewModelTest {

    // MARK: - Tests

    @Test
    fun onAppear_whenEverySourceSucceeds_showsAllRowsAndNoAlert() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("r111", "111"))),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(makeSuggestion("p222", "222"))),
        )

        sut.onAppear()

        assertEquals(setOf("report-r111", "photo-p222"), sut.rows.value.map { it.id }.toSet())
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenPhotoSuggestionsFail_stillShowsReportsAndAlerts() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("r111", "111"))),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(shouldThrow = true),
        )

        sut.onAppear()

        assertEquals("one failing query (e.g. rules or index not deployed yet) must not hide what the other sources loaded", listOf("report-r111"), sut.rows.value.map { it.id })
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenReportsFail_stillShowsPhotoSuggestionsAndAlerts() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(shouldThrow = true),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(makeSuggestion("p222", "222"))),
        )

        sut.onAppear()

        assertEquals(listOf("photo-p222"), sut.rows.value.map { it.id })
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenEverySourceFails_showsNoRowsAndAlerts() = runTest {
        val sut = makeSUT(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(shouldThrow = true),
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(shouldThrow = true),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(shouldThrow = true),
        )

        sut.onAppear()

        assertTrue(sut.rows.value.isEmpty())
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onRefresh_whenPhotoSuggestionsFailAfterALoad_keepsTheRowsAlreadyShownAndAlerts() = runTest {
        val suggestions = SwitchableFetchPhotoSuggestions(listOf(makeSuggestion("p222", "222")))
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("r111", "111"))),
            fetchPhotoSuggestions = suggestions,
        )
        sut.onAppear()
        suggestions.shouldThrow = true

        sut.onRefresh()

        assertEquals("a failed refresh must not make the maintainer read the queue as empty", setOf("report-r111", "photo-p222"), sut.rows.value.map { it.id }.toSet())
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_showsOneRowPerReport_evenForTheSameItem() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(
                stubbedReports = listOf(makeReport("a", "111", day(1)), makeReport("b", "111", day(2))),
            ),
        )

        sut.onAppear()

        assertEquals("two reports about different fields are answered separately", listOf("report-a", "report-b"), sut.rows.value.map { it.id })
    }

    @Test
    fun onRowResolved_forAReport_removesOnlyThatTicketsRow() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(
                stubbedReports = listOf(makeReport("a", "111", day(1)), makeReport("b", "111", day(2))),
            ),
        )
        sut.onAppear()

        sut.onRowResolved("report-a")

        assertEquals(listOf("report-b"), sut.rows.value.map { it.id })
    }

    @Test
    fun onAppear_sortsRowsOldestFirst() = runTest {
        val sut = makeSUT(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(stubbedSubmissions = listOf(makeSubmission("s", day(2)))),
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(
                stubbedReports = listOf(makeReport("late", "111", day(5)), makeReport("early", "111", day(1))),
            ),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(makeSuggestion("p222", "222", day(3)))),
        )

        sut.onAppear()

        assertEquals(
            "the queue is handled oldest first, each ticket by its own date",
            listOf("report-early", "submission-s", "photo-p222", "report-late"),
            sut.rows.value.map { it.id },
        )
    }

    @Test
    fun onRowResolved_forAPhoto_leavesOtherSuggestionsForTheSameItemWaiting() = runTest {
        val sut = makeSUT(
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(
                stubbedSuggestions = listOf(makeSuggestion("alice-ticket", "222", day(1)), makeSuggestion("bob-ticket", "222", day(2))),
            ),
        )
        sut.onAppear()

        sut.onRowResolved("photo-alice-ticket")

        assertEquals(
            "approving one suggestion leaves the others pending, so their rows must stay",
            listOf("photo-bob-ticket"),
            sut.rows.value.map { it.id },
        )
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchPendingSubmissions: FetchPendingSubmissionsUseCaseProtocol = FetchPendingSubmissionsUseCaseFake(),
        fetchFoodItemReports: FetchFoodItemReportsUseCaseProtocol = FetchFoodItemReportsUseCaseFake(),
        fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseProtocol = FetchPhotoSuggestionsUseCaseFake(),
    ): NotificationsViewModel = NotificationsViewModel(
        fetchPendingSubmissions = fetchPendingSubmissions,
        fetchFoodItemReports = fetchFoodItemReports,
        fetchPhotoSuggestions = fetchPhotoSuggestions,
        fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
        isMaintainer = true,
        fetchUserNotifications = FetchUserNotificationsUseCaseFake(),
        markUserNotificationSeen = MarkUserNotificationSeenUseCaseFake(),
        hideUserNotification = HideUserNotificationUseCaseFake(),
        withdrawUserNotification = WithdrawUserNotificationUseCaseFake(),
        notificationsStore = UserNotificationsStoreFake(),
    )

    private fun day(number: Long): Instant = Instant.ofEpochSecond(number * 86_400)

    private fun makeReport(id: String, barcode: String, date: Instant = Instant.now()) = FoodItemReportDomain(
        id = id,
        barcode = barcode,
        reportedBy = "bob",
        reason = "wrong data",
        reportedAt = date,
        status = TicketStatus.PENDING,
        locale = "en",
    )

    private fun makeSubmission(id: String, date: Instant) = FoodItemSubmissionDomain(
        id = id,
        barcode = "333",
        submittedBy = "carol",
        status = FoodItemSubmissionStatus.PENDING,
        submittedAt = date,
        rejectReason = null,
        item = FoodItemDomain(
            id = "333",
            kind = FoodItemKind.CATALOGUE,
            czName = "Tvaroh",
            engName = "",
            weight = 100.0,
            date = Instant.now(),
            energyKJ = 100.0,
            caloriesPerHundredGrams = 50.0,
            fat = 1.0,
            fatSaturated = 0.0,
            fatUnsaturatedFattyAcids = 1.0,
            carbohydrate = 1.0,
            carbohydratePureSugar = 1.0,
            fiber = 0.0,
            protein = 1.0,
            salt = 0.1,
        ),
    )

    private fun makeSuggestion(id: String, itemId: String, date: Instant = Instant.now()) = PhotoSuggestionDomain(
        id = id,
        itemId = itemId,
        suggestedBy = "alice",
        photoUrl = "https://example.com/photo.jpg",
        suggestedAt = date,
        status = TicketStatus.PENDING,
        locale = "en",
    )
}

private class SwitchableFetchPhotoSuggestions(private val stubbedSuggestions: List<PhotoSuggestionDomain>) : FetchPhotoSuggestionsUseCaseProtocol {
    var shouldThrow = false

    override suspend fun invoke(): List<PhotoSuggestionDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedSuggestions
    }
}
