package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.NotificationHiddenDTO
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.makeNotification
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HideUserNotificationUseCaseTest {

    // MARK: - Tests

    @Test
    fun hide_writesHiddenTrueToTheTicketAndDeletesNothing() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeNotification(kind = UserNotificationDomain.Kind.PHOTO_SUGGESTION, id = "photo-9"))

        assertEquals(1, dataProvider.updates.size)
        assertEquals("photo-9", dataProvider.updates.first().id)
        assertEquals(Constants.Firestore.PHOTO_SUGGESTIONS, dataProvider.updates.first().collection)
        assertEquals(NotificationHiddenDTO(hidden = true), dataProvider.updates.first().item)
        assertTrue("hiding is local to the user's list — the maintainer's ticket must stay", dataProvider.deletes.isEmpty())
    }

    @Test
    fun hide_whenTheWriteFails_throws() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)

        try {
            sut(makeNotification())
            fail("the list must be able to put the row back")
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun hide_whenNotAuthenticated_throws() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeNotification())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<HideUserNotificationUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return HideUserNotificationUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }
}
