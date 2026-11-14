package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.TicketResolutionDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResolveFoodItemReportUseCaseTest {

    // MARK: - Tests

    @Test
    fun resolve_writesStatusResponseResolvedAtAndHiddenFalse_andDeletesNothing() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("report-3", TicketStatus.ACCEPTED, "  Fixed, thanks  ")

        assertEquals(listOf("report-3"), dataProvider.updates.map { it.id })
        assertEquals(Constants.Firestore.FOOD_ITEM_REPORTS, dataProvider.updates.first().collection)
        val encoded = encoded(dataProvider)
        assertEquals("the resolve rule allows exactly these keys", setOf("status", "response", "resolved_at", "hidden"), encoded.keys)
        assertEquals("accepted", encoded["status"])
        assertEquals("Fixed, thanks", encoded["response"])
        assertEquals("a ticket the author hid while pending must come back", false, encoded["hidden"])
        assertTrue("the ticket is the record of the resolution and is kept", dataProvider.deletes.isEmpty())
    }

    @Test
    fun resolve_withRejected_writesRejected() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("report-3", TicketStatus.REJECTED, "Not reproducible")

        assertEquals("rejected", encoded(dataProvider)["status"])
    }

    @Test
    fun resolve_withEmptyOrWhitespaceResponse_throwsWithoutWriting() = runTest {
        val (sut, dataProvider) = makeSUT()

        for (response in listOf("", "   \n ")) {
            try {
                sut("report-3", TicketStatus.ACCEPTED, response)
                fail("Expected ResponseRequired error")
            } catch (_: TicketResolutionError.ResponseRequired) {
            }
        }

        assertTrue("the response is the only thing the author learns from a resolution", dataProvider.updates.isEmpty())
    }

    @Test
    fun resolve_withResponseOverTheLimitInUTF16Units_throwsWithoutWriting() = runTest {
        val (sut, dataProvider) = makeSUT()
        val tooLong = "😀".repeat(Constants.Firestore.RESPONSE_MAX_LENGTH)

        try {
            sut("report-3", TicketStatus.ACCEPTED, tooLong)
            fail("Expected ResponseTooLong error")
        } catch (_: TicketResolutionError.ResponseTooLong) {
        }

        assertTrue("the rules count UTF-16 units", dataProvider.updates.isEmpty())
    }

    @Test
    fun resolve_whenNotAuthenticated_throws() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("report-3", TicketStatus.ACCEPTED, "ok")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun resolve_whenTheWriteFails_throws() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)

        try {
            sut("report-3", TicketStatus.ACCEPTED, "ok")
            fail("the editor must keep the sheet open for a retry")
        } catch (_: java.io.IOException) {
        }
    }

    // MARK: - Helpers

    private fun encoded(dataProvider: TicketDataProviderFake): Map<String, Any?> {
        val dto = dataProvider.updates.first().item as TicketResolutionDTO
        return FirestoreDataMapper.encode(dto, TicketResolutionDTO.serializer())
    }

    private fun makeSUT(userId: String? = "maintainer"): Pair<ResolveFoodItemReportUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return ResolveFoodItemReportUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
