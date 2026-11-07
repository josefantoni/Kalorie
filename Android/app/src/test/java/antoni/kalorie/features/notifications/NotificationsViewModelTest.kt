package antoni.kalorie.features.notifications

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseFake
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseFake
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCaseProtocol
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
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("111"))),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(makeSuggestion("222"))),
        )

        sut.onAppear()

        assertEquals(setOf("report-111", "photo-222_alice"), sut.rows.value.map { it.id }.toSet())
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenPhotoSuggestionsFail_stillShowsReportsAndAlerts() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("111"))),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(shouldThrow = true),
        )

        sut.onAppear()

        assertEquals("one failing query (e.g. rules or index not deployed yet) must not hide what the other sources loaded", listOf("report-111"), sut.rows.value.map { it.id })
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenReportsFail_stillShowsPhotoSuggestionsAndAlerts() = runTest {
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(shouldThrow = true),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(makeSuggestion("222"))),
        )

        sut.onAppear()

        assertEquals(listOf("photo-222_alice"), sut.rows.value.map { it.id })
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
        val suggestions = SwitchableFetchPhotoSuggestions(listOf(makeSuggestion("222")))
        val sut = makeSUT(
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("111"))),
            fetchPhotoSuggestions = suggestions,
        )
        sut.onAppear()
        suggestions.shouldThrow = true

        sut.onRefresh()

        assertEquals("a failed refresh must not make the maintainer read the queue as empty", setOf("report-111", "photo-222_alice"), sut.rows.value.map { it.id }.toSet())
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onReportRowChanged_withRemainingReports_keepsTheRowWithOnlyThose() = runTest {
        val sut = makeSUT(fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("111"), makeReport("111"))))
        sut.onAppear()

        sut.onReportRowChanged("report-111", listOf(makeReport("111")))

        val kind = sut.rows.value.single().kind as NotificationRow.Kind.Report
        assertEquals(1, kind.reports.size)
    }

    @Test
    fun onReportRowChanged_withNoRemainingReports_removesTheRow() = runTest {
        val sut = makeSUT(fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("111"))))
        sut.onAppear()

        sut.onReportRowChanged("report-111", emptyList())

        assertTrue(sut.rows.value.isEmpty())
    }

    @Test
    fun onAppear_sortsRowsOldestFirst_andDatesAReportGroupByItsOldestReport() = runTest {
        val sut = makeSUT(
            fetchPendingSubmissions = FetchPendingSubmissionsUseCaseFake(stubbedSubmissions = listOf(makeSubmission("s", day(2)))),
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(
                stubbedReports = listOf(makeReport("111", day(5)), makeReport("111", day(1))),
            ),
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions = listOf(makeSuggestion("222", day(3)))),
        )

        sut.onAppear()

        assertEquals(
            "the queue is handled oldest first; a report group counts from its oldest report, so days 1 and 5 sort before a suggestion from day 3",
            listOf("report-111", "submission-s", "photo-222_alice"),
            sut.rows.value.map { it.id },
        )
    }

    @Test
    fun onPhotoSuggestionResolved_whenApproved_dropsEverySuggestionRowForThatItem() = runTest {
        val sut = makeSUT(
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(
                stubbedSuggestions = listOf(
                    makeSuggestion("222", day(1), "alice"),
                    makeSuggestion("222", day(2), "bob"),
                    makeSuggestion("333", day(3), "alice"),
                ),
            ),
            fetchFoodItemReports = FetchFoodItemReportsUseCaseFake(stubbedReports = listOf(makeReport("222"))),
        )
        sut.onAppear()

        sut.onPhotoSuggestionResolved("photo-222_alice", isApproved = true)

        assertEquals(
            "approval deletes every suggestion for the item; reports and other items stay",
            setOf("photo-333_alice", "report-222"),
            sut.rows.value.map { it.id }.toSet(),
        )
    }

    @Test
    fun onPhotoSuggestionResolved_whenRejected_dropsOnlyThatRow() = runTest {
        val sut = makeSUT(
            fetchPhotoSuggestions = FetchPhotoSuggestionsUseCaseFake(
                stubbedSuggestions = listOf(makeSuggestion("222", day(1), "alice"), makeSuggestion("222", day(2), "bob")),
            ),
        )
        sut.onAppear()

        sut.onPhotoSuggestionResolved("photo-222_alice", isApproved = false)

        assertEquals(listOf("photo-222_bob"), sut.rows.value.map { it.id })
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
    )

    private fun day(number: Long): Instant = Instant.ofEpochSecond(number * 86_400)

    private fun makeReport(barcode: String, date: Instant = Instant.now()) = FoodItemReportDomain(
        barcode = barcode,
        reportedBy = "bob",
        reason = "wrong data",
        reportedAt = date,
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

    private fun makeSuggestion(itemId: String, date: Instant = Instant.now(), suggestedBy: String = "alice") = PhotoSuggestionDomain(
        itemId = itemId,
        suggestedBy = suggestedBy,
        photoUrl = "https://example.com/photo.jpg",
        suggestedAt = date,
    )
}

private class SwitchableFetchPhotoSuggestions(private val stubbedSuggestions: List<PhotoSuggestionDomain>) : FetchPhotoSuggestionsUseCaseProtocol {
    var shouldThrow = false

    override suspend fun invoke(): List<PhotoSuggestionDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedSuggestions
    }
}
