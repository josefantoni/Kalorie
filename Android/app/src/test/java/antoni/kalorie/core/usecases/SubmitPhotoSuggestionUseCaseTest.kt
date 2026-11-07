package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.PhotoSuggestionDTO
import antoni.kalorie.core.networking.PhotoSuggestionDataProviderFake
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

class SubmitPhotoSuggestionUseCaseTest {

    // MARK: - Tests

    @Test
    fun submit_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _, _) = makeSUT(userId = null)

        try {
            sut("12345678", byteArrayOf(1))
            fail("Expected NotAuthenticated")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun submit_clearsAStaleOwnFileBeforeUploading_soARetryIsNotBlockedByTheRulesDenyingOverwrite() = runTest {
        val (sut, _, storage) = makeSUT()
        val staleUrl = storage.uploadAsync(byteArrayOf(9), PATH, "image/jpeg")

        sut("12345678", byteArrayOf(1))

        assertEquals("the fixed path must be cleared before the new upload", PATH, storage.deletedPaths.first())
        assertArrayEquals("the new photo replaces the stale one", byteArrayOf(1), storage.files[staleUrl])
    }

    @Test
    fun submit_whenASuggestionIsAlreadyPending_touchesNeitherTheStoredFileNorTheDocument() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        val pendingUrl = storage.uploadAsync(byteArrayOf(9), PATH, "image/jpeg")
        dataProvider.stubbedExistingSuggestions["12345678_alice"] = PhotoSuggestionDTO("12345678", "alice", pendingUrl, Instant.now())
        val uploadsBefore = storage.uploadedPaths.toList()

        sut("12345678", byteArrayOf(1))

        assertTrue("the rules deny update, so deleting the pending file would leave a document with a dead photo_url", storage.deletedPaths.isEmpty())
        assertEquals(uploadsBefore, storage.uploadedPaths)
        assertArrayEquals(byteArrayOf(9), storage.files[pendingUrl])
        assertTrue(dataProvider.setCalls.isEmpty())
    }

    @Test
    fun submit_whenTheExistingSuggestionCannotBeRead_throwsAndTouchesNoFile() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        storage.uploadAsync(byteArrayOf(9), PATH, "image/jpeg")
        dataProvider.stubbedLoadError = IOException("offline")

        try {
            sut("12345678", byteArrayOf(1))
            fail("Expected the read error")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }

        assertTrue(storage.deletedPaths.isEmpty())
    }

    @Test
    fun submit_whenTheStaleFileCannotBeDeleted_stillUploads() = runTest {
        val (sut, _, storage) = makeSUT()
        storage.deleteError = IOException("gone")

        sut("12345678", byteArrayOf(1))

        assertEquals(listOf(PATH), storage.uploadedPaths)
    }

    @Test
    fun submit_writesAPhotoUrlThatPointsAtTheSuggestionFolderUnderTheItemAndUserId() = runTest {
        val (sut, dataProvider, _) = makeSUT()

        sut("12345678", byteArrayOf(1))

        val (id, collection, item) = dataProvider.setCalls.first()
        assertEquals(Constants.Firestore.PHOTO_SUGGESTIONS, collection)
        assertEquals("12345678_alice", id)
        val dto = item as PhotoSuggestionDTO
        assertEquals("alice", dto.suggestedBy)
        assertEquals("12345678", dto.itemId)
        assertTrue("the rules only accept the author's own suggestion file for that item", dto.photoUrl.contains("photoSuggestions%2Falice%2F12345678.jpg"))
    }

    @Test
    fun submit_whenTheFirestoreWriteFails_deletesTheUploadedFileAndRethrows() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        dataProvider.stubbedSetError = IOException("offline")

        try {
            sut("12345678", byteArrayOf(1))
            fail("Expected the write error")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }

        assertTrue("an uploaded file without a document is invisible to the maintainer and can never be reviewed", storage.files.isEmpty())
    }

    @Test
    fun submit_whenTheWriteFailsButAConcurrentAttemptCreatedTheDocument_keepsTheFileThatDocumentPointsTo() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        dataProvider.stubbedSetError = IOException("connection lost")
        dataProvider.suggestionsAppearingAfterFailedSet["12345678_alice"] =
            PhotoSuggestionDTO("12345678", "alice", "https://example.com/photoSuggestions/alice/12345678.jpg", Instant.now())

        try {
            sut("12345678", byteArrayOf(1))
            fail("Expected the write error")
        } catch (error: IOException) {
            assertEquals("connection lost", error.message)
        }

        assertEquals("deleting the file would leave the concurrent attempt's document pointing at nothing", 1, storage.files.size)
    }

    @Test
    fun submit_whenTheUploadFails_writesNoDocument() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        storage.uploadError = IOException("timed out")

        try {
            sut("12345678", byteArrayOf(1))
        } catch (_: IOException) {
        }

        assertTrue(dataProvider.setCalls.isEmpty())
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "alice"): Triple<SubmitPhotoSuggestionUseCase, PhotoSuggestionDataProviderFake, StorageDataProviderFake> {
        val dataProvider = PhotoSuggestionDataProviderFake()
        val storage = StorageDataProviderFake()
        val sut = SubmitPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = userId), storage)
        return Triple(sut, dataProvider, storage)
    }

    private companion object {
        const val PATH = "photoSuggestions/alice/12345678.jpg"
    }
}
