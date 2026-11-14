package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeReportDTO
import antoni.kalorie.core.networking.makeSuggestionDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class FetchUserNotificationsUseCaseTest {

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
    fun fetch_queriesOnlyTheUsersVisibleTickets_inBothCollections() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-42")

        sut()

        val reports = TicketDataProviderFake.OrderedQuery(
            Constants.Firestore.FOOD_ITEM_REPORTS,
            listOf("reported_by" to "user-42"),
            isHidden = false,
            orderField = "reported_at",
            descending = true,
        )
        val suggestions = TicketDataProviderFake.OrderedQuery(
            Constants.Firestore.PHOTO_SUGGESTIONS,
            listOf("suggested_by" to "user-42"),
            isHidden = false,
            orderField = "suggested_at",
            descending = true,
        )
        assertEquals(
            "hidden tickets must never be downloaded — Firestore cannot filter on a comparison of two fields",
            setOf(reports, suggestions),
            dataProvider.orderedQueries.toSet(),
        )
    }

    @Test
    fun fetch_mapsStateResponseAndDatesOfAResolvedReport() = runTest {
        val (sut, dataProvider) = makeSUT()
        val resolvedAt = Instant.ofEpochSecond(1_700_100_000)
        dataProvider.stubbedReports = listOf(makeReportDTO(id = "report-1", status = TicketStatus.ACCEPTED, response = "Fixed, thanks", resolvedAt = resolvedAt))

        val notification = sut().first()

        assertEquals("report-1", notification.id)
        assertEquals(UserNotificationDomain.Kind.REPORT, notification.kind)
        assertEquals(TicketStatus.ACCEPTED, notification.status)
        assertEquals("Fixed, thanks", notification.response)
        assertEquals(resolvedAt, notification.resolvedAt)
        assertEquals("wrong fat", notification.reason)
    }

    @Test
    fun fetch_aTicketResolvedAfterTheUserSawIt_isUnreadAgain() = runTest {
        val (sut, dataProvider) = makeSUT()
        val created = Instant.ofEpochSecond(1_700_000_000)
        dataProvider.stubbedReports = listOf(
            makeReportDTO(
                reportedAt = created,
                status = TicketStatus.ACCEPTED,
                response = "Done",
                resolvedAt = created.plusSeconds(3_600),
                seenAt = created.plusSeconds(60),
            ),
        )

        val notification = sut().first()

        assertTrue("the resolution is a new event, so a read marker from before it no longer counts", notification.isUnread)
    }

    @Test
    fun fetch_aTicketSeenAfterItsLastEvent_isRead() = runTest {
        val (sut, dataProvider) = makeSUT()
        val created = Instant.ofEpochSecond(1_700_000_000)
        dataProvider.stubbedReports = listOf(makeReportDTO(reportedAt = created, seenAt = created.plusSeconds(60)))

        val notification = sut().first()

        assertFalse(notification.isUnread)
    }

    @Test
    fun fetch_ordersBothKindsByTheirLastEvent_newestFirst() = runTest {
        val (sut, dataProvider) = makeSUT()
        val base = Instant.ofEpochSecond(1_700_000_000)
        dataProvider.stubbedReports = listOf(
            makeReportDTO(id = "old-report", reportedAt = base),
            makeReportDTO(id = "resolved-report", reportedAt = base.plusSeconds(10), status = TicketStatus.ACCEPTED, response = "ok", resolvedAt = base.plusSeconds(500)),
        )
        dataProvider.stubbedSuggestions = listOf(makeSuggestionDTO(id = "photo", suggestedAt = base.plusSeconds(100)))

        val ids = sut().map { it.id }

        assertEquals(listOf("resolved-report", "photo", "old-report"), ids)
    }

    @Test
    fun fetch_aResolvedPhoto_hasNoPreviewBecauseItsFileIsDeleted() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = listOf(
            makeSuggestionDTO(id = "pending-photo"),
            makeSuggestionDTO(id = "approved-photo", status = TicketStatus.ACCEPTED, response = "ok", resolvedAt = Instant.ofEpochSecond(1_700_100_000)),
        )

        val byId = sut().associateBy { it.id }

        assertNotNull(byId["pending-photo"]?.photoUrl)
        assertNull(byId["approved-photo"]?.photoUrl)
    }

    @Test
    fun fetch_usesTheDocumentIdAsTheNotificationId_andACollectionQualifiedRowId() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedReports = listOf(makeReportDTO(id = "same-id"))
        dataProvider.stubbedSuggestions = listOf(makeSuggestionDTO(id = "same-id"))

        val notifications = sut()

        assertEquals(setOf("foodItemReports/same-id", "photoSuggestions/same-id"), notifications.map { it.rowId }.toSet())
    }

    @Test
    fun fetch_whenEitherQueryFails_throws() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.failingLoadCollections = setOf(Constants.Firestore.PHOTO_SUGGESTIONS)

        try {
            sut()
            fail("a half list would show a bell without the missing tickets")
        } catch (_: Exception) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchUserNotificationsUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        val sut = FetchUserNotificationsUseCase(dataProvider, AuthProviderFake(userId = userId))
        return sut to dataProvider
    }
}
