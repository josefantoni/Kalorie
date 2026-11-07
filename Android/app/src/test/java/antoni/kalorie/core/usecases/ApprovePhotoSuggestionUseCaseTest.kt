package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.PhotoSuggestionDataProviderFake
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.makePhotoSuggestionItem
import antoni.kalorie.core.utils.Constants
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

class ApprovePhotoSuggestionUseCaseTest {

    // MARK: - Tests

    @Test
    fun approve_givesTheCatalogueItemAPhotoInCatalogPhotos_neverALinkIntoThePrivateSuggestionFolder() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeSuggestion(storage)

        sut(suggestion)

        val dto = dataProvider.setCalls.first { it.second == Constants.Firestore.FOOD_ITEMS }.third as FoodItemDTO
        val photoUrl = dto.photoUrl.orEmpty()
        assertTrue(photoUrl.contains("catalogPhotos%2F12345678%2F"))
        assertFalse("the author keeps delete rights on the suggestion folder, so every user's photo must not live there", photoUrl.contains("photoSuggestions"))
    }

    @Test
    fun approve_whenTheCatalogueItemHasZeroCalories_stillAttachesThePhoto() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem(caloriesPerHundredGrams = 0.0))
        val suggestion = makeSuggestion(storage)

        sut(suggestion)

        val dto = dataProvider.setCalls.first { it.second == Constants.Firestore.FOOD_ITEMS }.third as FoodItemDTO
        assertNotNull("approving a photo must not re-validate an item the moderator did not edit", dto.photoUrl)
        assertEquals(0.0, dto.caloriesPerHundredGrams, 0.0)
    }

    @Test
    fun approve_deletesEveryUsersSuggestionForTheItem_filesAndDocuments() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeSuggestion(storage)
        dataProvider.stubbedSuggestionDocumentIds = listOf("12345678_alice", "12345678_bob")

        sut(suggestion)

        val deleted = dataProvider.deletedIds.filter { it.second == Constants.Firestore.PHOTO_SUGGESTIONS }.map { it.first }
        assertEquals(setOf("12345678_alice", "12345678_bob"), deleted.toSet())
        assertEquals(
            setOf("photoSuggestions/alice/12345678.jpg", "photoSuggestions/bob/12345678.jpg"),
            storage.deletedPaths.toSet(),
        )
    }

    @Test
    fun approve_whenTheItemAlreadyHasAPhoto_throwsAndOverwritesNothing() = runTest {
        val item = makePhotoSuggestionItem(photoUrl = "https://example.com/catalogPhotos/12345678/x.jpg")
        val (sut, dataProvider, storage) = makeSUT(currentItem = item)
        val suggestion = makeSuggestion(storage)

        try {
            sut(suggestion)
            fail("Expected ItemAlreadyHasPhoto")
        } catch (_: ApprovePhotoSuggestionError.ItemAlreadyHasPhoto) {
        }

        assertTrue(dataProvider.setCalls.isEmpty())
        assertTrue("the suggestion stays so the maintainer can still reject it", dataProvider.deletedIds.isEmpty())
    }

    @Test
    fun approve_whenTheItemIsGone_throwsItemMissing() = runTest {
        val (sut, _, storage) = makeSUT(currentItem = null)
        val suggestion = makeSuggestion(storage)

        try {
            sut(suggestion)
            fail("Expected ItemMissing")
        } catch (_: ApprovePhotoSuggestionError.ItemMissing) {
        }
    }

    @Test
    fun approve_whenTheCatalogueWriteFails_keepsTheSuggestionsAndDoesNotLeaveACatalogueFile() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeSuggestion(storage)
        dataProvider.stubbedSetError = IOException("offline")

        try {
            sut(suggestion)
        } catch (_: IOException) {
        }

        assertTrue(dataProvider.deletedIds.isEmpty())
        assertFalse(storage.files.keys.any { it.contains("catalogPhotos") })
    }

    @Test
    fun reject_deletesTheFileAndTheDocumentOfThatSuggestionOnly() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeSuggestion(storage)
        val sut = RejectPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = "maintainer"), storage)

        sut(suggestion)

        assertEquals(listOf("photoSuggestions/alice/12345678.jpg"), storage.deletedPaths)
        assertEquals(listOf("12345678_alice"), dataProvider.deletedIds.map { it.first })
    }

    @Test
    fun reject_whenTheFileIsAlreadyGone_stillDeletesTheDocument() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeSuggestion(storage)
        storage.deleteError = StorageException.fromExceptionAndHttpCode(null, 404)
        val sut = RejectPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = "maintainer"), storage)

        sut(suggestion)

        assertEquals("a missing file must not keep the row in the queue forever", listOf("12345678_alice"), dataProvider.deletedIds.map { it.first })
    }

    @Test
    fun reject_whenTheFileDeleteFailsForAnotherReason_keepsTheDocumentSoItCanBeRetried() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeSuggestion(storage)
        storage.deleteError = IOException("offline")
        val sut = RejectPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = "maintainer"), storage)

        try {
            sut(suggestion)
            fail("the failure must reach the caller")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }

        assertTrue("deleting the document would orphan the file with nothing left to retry from", dataProvider.deletedIds.isEmpty())
    }

    @Test
    fun approve_whenCancelledWhileTheCatalogueWriteIsInFlight_stillRemovesTheSuggestions() = runTest {
        val dataProvider = PhotoSuggestionDataProviderFake()
        dataProvider.currentItem = makePhotoSuggestionItem()
        dataProvider.stubbedSuggestionDocumentIds = listOf("12345678_alice")
        val storage = StorageDataProviderFake()
        val sut = ApprovePhotoSuggestionUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = "maintainer"),
            storageProvider = storage,
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = FoodItemDTO(makePhotoSuggestionItem()).asDomain()),
            downloadFoodPhoto = DownloadFoodPhotoUseCase(storage),
            updateFoodItem = SlowUpdateFoodItemUseCase(),
        )
        val suggestion = makeSuggestion(storage)

        val job = launch { sut(suggestion) }
        yield()
        job.cancel()
        advanceUntilIdle()

        assertEquals(
            "leaving the screen mid-approval must not leave a photo on the item with its suggestions still pending",
            listOf("12345678_alice"),
            dataProvider.deletedIds.map { it.first },
        )
    }

    @Test
    fun reject_whenCancelledWhileTheFileDeleteIsInFlight_stillDeletesTheDocument() = runTest {
        val dataProvider = PhotoSuggestionDataProviderFake()
        val storage = StorageDataProviderFake()
        val suggestion = makeSuggestion(storage)
        val sut = RejectPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = "maintainer"), SlowDeleteStorageDataProviderFake(storage))

        val job = launch { sut(suggestion) }
        yield()
        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf("12345678_alice"), dataProvider.deletedIds.map { it.first })
    }

    // MARK: - Helpers

    private fun makeSUT(currentItem: FoodItemDomain?): Triple<ApprovePhotoSuggestionUseCase, PhotoSuggestionDataProviderFake, StorageDataProviderFake> {
        val dataProvider = PhotoSuggestionDataProviderFake()
        dataProvider.currentItem = currentItem
        val storage = StorageDataProviderFake()
        val authProvider = AuthProviderFake(userId = "maintainer")
        val sut = ApprovePhotoSuggestionUseCase(
            dataProvider = dataProvider,
            authProvider = authProvider,
            storageProvider = storage,
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = currentItem?.let { FoodItemDTO(it).asDomain() }),
            downloadFoodPhoto = DownloadFoodPhotoUseCase(storage),
            updateFoodItem = UpdateFoodItemUseCase(
                dataProvider = dataProvider,
                authProvider = authProvider,
                uploadFoodPhoto = UploadFoodPhotoUseCase(storage),
                deleteFoodPhoto = DeleteFoodPhotoUseCase(storage),
            ),
        )
        return Triple(sut, dataProvider, storage)
    }

    private suspend fun makeSuggestion(storage: StorageDataProviderFake): PhotoSuggestionDomain {
        val url = storage.uploadAsync(byteArrayOf(7), "photoSuggestions/alice/12345678.jpg", "image/jpeg")
        return PhotoSuggestionDomain(itemId = "12345678", suggestedBy = "alice", photoUrl = url, suggestedAt = Instant.now())
    }
}

private class SlowUpdateFoodItemUseCase : UpdateFoodItemUseCaseProtocol {
    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Boolean) {
        delay(1_000)
    }
}

private class SlowDeleteStorageDataProviderFake(
    private val base: StorageDataProviderFake,
) : StorageDataProviderProtocol by base {
    override suspend fun deleteAtPathAsync(path: String) {
        delay(1_000)
        base.deleteAtPathAsync(path)
    }
}
