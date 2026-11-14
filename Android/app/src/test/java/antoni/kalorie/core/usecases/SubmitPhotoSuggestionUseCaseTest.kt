package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.PhotoSuggestionCreateDTO
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.features.notifications.UserNotificationsStoreFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SubmitPhotoSuggestionUseCaseTest {

    // MARK: - Tests

    @Test
    fun submitPhotoSuggestion_whenNotAuthenticated_throwsAndTouchesNothing() = runTest {
        val (sut, dataProvider, storage) = makeSUT(userId = null)

        try {
            sut("12345678", byteArrayOf(1))
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertTrue(storage.uploadedPaths.isEmpty())
        assertTrue(dataProvider.sets.isEmpty())
    }

    @Test
    fun submitPhotoSuggestion_uploadsToAPathWithTheItemAndTheSuggestionId() = runTest {
        val (sut, dataProvider, storage) = makeSUT(userId = "user-42")
        dataProvider.newIds = mutableListOf("suggestion-id")

        sut("12345678", byteArrayOf(1))

        assertEquals(
            "the Storage rule reads the item from the path and the Firestore rule matches the suggestion id inside photo_url",
            listOf("photoSuggestions/user-42/12345678/suggestion-id.jpg"),
            storage.uploadedPaths,
        )
        assertEquals(listOf("suggestion-id"), dataProvider.sets.map { it.id })
        val photoUrl = encoded(dataProvider)["photo_url"] as String
        assertTrue(photoUrl.contains("%2Fuser-42%2F12345678%2Fsuggestion-id.jpg"))
    }

    @Test
    fun submitPhotoSuggestion_writesAPendingVisibleTicketWithTheAppLocaleAndNoMaintainerKeys() = runTest {
        val (sut, dataProvider, _) = makeSUT(locale = "cs")

        sut("12345678", byteArrayOf(1))

        val encoded = encoded(dataProvider)
        assertEquals(setOf("item_id", "suggested_by", "suggested_at", "photo_url", "status", "locale", "hidden"), encoded.keys)
        assertEquals("pending", encoded["status"])
        assertEquals(false, encoded["hidden"])
        assertEquals("cs", encoded["locale"])
    }

    @Test
    fun submitPhotoSuggestion_twiceForTheSameItem_usesTwoDifferentFiles() = runTest {
        val (sut, _, storage) = makeSUT()

        sut("12345678", byteArrayOf(1))
        sut("12345678", byteArrayOf(2))

        assertEquals("a resolved ticket no longer blocks a new suggestion for the item", 2, storage.uploadedPaths.toSet().size)
    }

    @Test
    fun submitPhotoSuggestion_afterASuccessfulWrite_marksThatTheUserMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val (sut, _, _) = makeSUT(store = store)

        sut("12345678", byteArrayOf(1))

        assertTrue("without it the gated bell would not query and the new notification would wait up to a day", store.mayHaveUnread)
        assertNotNull("a fetch already in flight must not overwrite the flag with a result that predates the submit", store.lastSubmittedAt)
    }

    @Test
    fun submitPhotoSuggestion_whenTheUploadFails_writesNothingAndDoesNotMarkMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val (sut, dataProvider, storage) = makeSUT(store = store)
        storage.uploadError = IOException("timed out")

        try {
            sut("12345678", byteArrayOf(1))
        } catch (_: IOException) {
        }

        assertTrue(dataProvider.sets.isEmpty())
        assertFalse(store.mayHaveUnread)
    }

    @Test
    fun submitPhotoSuggestion_whenTheDocumentWriteFails_deletesTheUploadedFileAndDoesNotMarkMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val (sut, dataProvider, storage) = makeSUT(userId = "user-42", store = store)
        dataProvider.newIds = mutableListOf("suggestion-id")
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.PHOTO_SUGGESTIONS)

        try {
            sut("12345678", byteArrayOf(1))
            fail("the failure must reach the caller")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }

        assertEquals(
            "an orphaned file would be unreachable for the user and invisible to the maintainer",
            listOf("photoSuggestions/user-42/12345678/suggestion-id.jpg"),
            storage.deletedPaths,
        )
        assertFalse(store.mayHaveUnread)
    }

    // MARK: - Helpers

    private fun encoded(dataProvider: TicketDataProviderFake): Map<String, Any?> {
        val dto = dataProvider.sets.first().item as PhotoSuggestionCreateDTO
        return FirestoreDataMapper.encode(dto, PhotoSuggestionCreateDTO.serializer())
    }

    private fun makeSUT(
        userId: String? = "test-user",
        store: UserNotificationsStoreFake = UserNotificationsStoreFake(),
        locale: String = "en",
    ): Triple<SubmitPhotoSuggestionUseCase, TicketDataProviderFake, StorageDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        val storage = StorageDataProviderFake()
        val sut = SubmitPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = userId), storage, store, locale)
        return Triple(sut, dataProvider, storage)
    }
}
