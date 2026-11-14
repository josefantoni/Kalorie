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

class FetchMyFoodItemReportUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchMyFoodItemReport_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("12345678")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchMyFoodItemReport_asksOnlyForTheUsersPendingTicketForThatItem() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")

        sut("12345678")

        assertEquals(
            "a resolved ticket must not block a new report, so the query is pending only",
            listOf(
                TicketDataProviderFake.EqualityQuery(
                    Constants.Firestore.FOOD_ITEM_REPORTS,
                    listOf("reported_by" to "user-42", "barcode" to "12345678", "status" to "pending"),
                    limit = 1,
                ),
            ),
            dataProvider.equalityQueries,
        )
    }

    @Test
    fun fetchMyFoodItemReport_whenNoPendingTicket_returnsNull() = runTest {
        val (sut, _) = makeSUT()

        assertNull(sut("12345678"))
    }

    @Test
    fun fetchMyFoodItemReport_whenAPendingTicketExists_returnsIt() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")
        dataProvider.stubbedReports = listOf(makeReportDTO(id = "report-7", reportedBy = "user-42"))

        assertEquals("report-7", sut("12345678")?.id)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchMyFoodItemReportUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return FetchMyFoodItemReportUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
