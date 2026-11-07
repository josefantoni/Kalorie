package antoni.kalorie.features.notifications

import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.usecases.DeleteFoodItemReportUseCaseFake
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
        deleteFoodItemReport = DeleteFoodItemReportUseCaseFake(),
    )

    private fun makeReport(barcode: String) = FoodItemReportDomain(barcode = barcode, reportedBy = "bob", reason = "wrong data", reportedAt = Instant.now())

    private fun makeSuggestion(itemId: String) = PhotoSuggestionDomain(
        itemId = itemId,
        suggestedBy = "alice",
        photoUrl = "https://example.com/photo.jpg",
        suggestedAt = Instant.now(),
    )
}

private class SwitchableFetchPhotoSuggestions(private val stubbedSuggestions: List<PhotoSuggestionDomain>) : FetchPhotoSuggestionsUseCaseProtocol {
    var shouldThrow = false

    override suspend fun invoke(): List<PhotoSuggestionDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedSuggestions
    }
}
