package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeReportDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FetchFoodItemReportsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetch_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetch_loadsOnlyPendingReports_newestFirst() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        val expected = TicketDataProviderFake.OrderedQuery(
            Constants.Firestore.FOOD_ITEM_REPORTS,
            listOf("status" to "pending"),
            isHidden = null,
            orderField = "reported_at",
            descending = true,
        )
        assertEquals("resolved tickets are kept for their authors and must not clutter the maintainer's queue", listOf(expected), dataProvider.orderedQueries)
    }

    @Test
    fun fetch_returnsOneEntryPerTicket_evenForTheSameItem() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedReports = listOf(makeReportDTO(id = "a", barcode = "12345678"), makeReportDTO(id = "b", barcode = "12345678"))

        val reports = sut()

        assertEquals("two reports about different fields are answered separately", listOf("a", "b"), reports.map { it.id })
    }

    @Test
    fun fetch_keepsTheOwnerlessTicketOfADeletedAccount() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedReports = listOf(makeReportDTO(id = "orphan", reportedBy = null))

        val reports = sut()

        assertEquals(listOf("orphan"), reports.map { it.id })
        assertNull(reports.first().reportedBy)
    }

    @Test
    fun fetch_whenTheQueryFails_throws() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.failingLoadCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)

        try {
            sut()
            fail("Expected the query error")
        } catch (_: java.io.IOException) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "maintainer"): Pair<FetchFoodItemReportsUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return FetchFoodItemReportsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
