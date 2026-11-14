package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.NotificationSeenDTO
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeNotification
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class MarkUserNotificationSeenUseCaseTest {

    // MARK: - Tests

    @Test
    fun markSeen_whenUnread_writesOneSeenAtToTheTicketDocument() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeNotification(kind = UserNotificationDomain.Kind.PHOTO_SUGGESTION, id = "photo-7"))

        assertEquals(1, dataProvider.updates.size)
        assertEquals("photo-7", dataProvider.updates.first().id)
        assertEquals(Constants.Firestore.PHOTO_SUGGESTIONS, dataProvider.updates.first().collection)
        assertTrue(dataProvider.updates.first().item is NotificationSeenDTO)
    }

    @Test
    fun markSeen_whenAlreadyRead_writesNothing() = runTest {
        val (sut, dataProvider) = makeSUT()
        val created = Instant.ofEpochSecond(1_700_000_000)

        sut(makeNotification(createdAt = created, seenAt = created.plusSeconds(10)))

        assertTrue("opening the detail of a read notification must cost nothing", dataProvider.updates.isEmpty())
    }

    @Test
    fun markSeen_whenSeenBeforeTheResolution_writesAgain() = runTest {
        val (sut, dataProvider) = makeSUT()
        val created = Instant.ofEpochSecond(1_700_000_000)

        sut(
            makeNotification(
                createdAt = created,
                status = TicketStatus.ACCEPTED,
                resolvedAt = created.plusSeconds(3_600),
                seenAt = created.plusSeconds(10),
            ),
        )

        assertEquals("the resolution is a new event and the rules allow repeated seen_at writes", 1, dataProvider.updates.size)
    }

    @Test
    fun markSeen_whenTheWriteFails_throwsSoTheRowStaysUnread() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)

        try {
            sut(makeNotification())
            fail("a swallowed failure would show the row as read although nothing was stored")
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun markSeen_whenNotAuthenticated_throws() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeNotification())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<MarkUserNotificationSeenUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return MarkUserNotificationSeenUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
