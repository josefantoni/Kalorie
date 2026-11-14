package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.networking.TicketResolutionDTO
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
import org.junit.Assert.assertNotEquals
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
        val suggestion = makeStoredSuggestion(storage)

        sut(suggestion, "Thanks")

        val dto = dataProvider.sets.first { it.collection == Constants.Firestore.FOOD_ITEMS }.item as FoodItemDTO
        val photoUrl = dto.photoUrl.orEmpty()
        assertTrue(photoUrl.contains("catalogPhotos%2F12345678%2F"))
        assertFalse("the author keeps delete rights on the suggestion folder, so every user's photo must not live there", photoUrl.contains("photoSuggestions"))
    }

    @Test
    fun approve_whenTheCatalogueItemHasZeroCalories_stillAttachesThePhoto() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem(caloriesPerHundredGrams = 0.0))
        val suggestion = makeStoredSuggestion(storage)

        sut(suggestion, "Thanks")

        val dto = dataProvider.sets.first { it.collection == Constants.Firestore.FOOD_ITEMS }.item as FoodItemDTO
        assertNotNull("approving a photo must not re-validate an item the moderator did not edit", dto.photoUrl)
        assertEquals(0.0, dto.caloriesPerHundredGrams, 0.0)
    }

    @Test
    fun approve_whenTheItemAlreadyHasAPhoto_replacesItAndDeletesTheOldFile() = runTest {
        val existing = "https://storage.fake/catalogPhotos%2F12345678%2Fold.jpg"
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem(photoUrl = existing))
        storage.files[existing] = byteArrayOf(9)
        val suggestion = makeStoredSuggestion(storage)

        sut(suggestion, "Thanks")

        val dto = dataProvider.sets.first { it.collection == Constants.Firestore.FOOD_ITEMS }.item as FoodItemDTO
        assertNotEquals("a later suggestion that was pending before the item got its photo replaces it", existing, dto.photoUrl)
        assertTrue("the replaced catalogue photo must not stay in Storage", storage.deletedUrls.contains(existing))
    }

    @Test
    fun approve_writesTheResolution_thenDeletesTheSuggestionFile_andLeavesTheDocument() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeStoredSuggestion(storage)

        sut(suggestion, "  Thanks for the photo!  ")

        val update = dataProvider.updates.first()
        assertEquals(Constants.Firestore.PHOTO_SUGGESTIONS, update.collection)
        assertEquals(suggestion.id, update.id)
        val encoded = FirestoreDataMapper.encode(update.item as TicketResolutionDTO, TicketResolutionDTO.serializer())
        assertEquals("accepted", encoded["status"])
        assertEquals("Thanks for the photo!", encoded["response"])
        assertEquals(false, encoded["hidden"])
        assertTrue("the file is deleted once the ticket records the outcome", storage.deletedUrls.contains(suggestion.photoUrl))
        assertTrue("the ticket is kept so its author can read the response", dataProvider.deletes.isEmpty())
    }

    @Test
    fun approve_leavesOtherUsersPendingSuggestionsForTheItemAlone() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeStoredSuggestion(storage)
        dataProvider.stubbedDocumentIds = mapOf(Constants.Firestore.PHOTO_SUGGESTIONS to listOf(suggestion.id, "other-user-ticket"))

        sut(suggestion, "Thanks")

        assertEquals("approving one suggestion must not resolve or delete the others", listOf(suggestion.id), dataProvider.updates.map { it.id })
        assertTrue(dataProvider.deletes.isEmpty())
        assertEquals(listOf(suggestion.photoUrl), storage.deletedUrls)
    }

    @Test
    fun approve_withEmptyResponse_throwsAndChangesNothing() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeStoredSuggestion(storage)

        try {
            sut(suggestion, "  ")
            fail("Expected ResponseRequired error")
        } catch (_: TicketResolutionError.ResponseRequired) {
        }

        assertTrue("the catalogue must not change when the ticket cannot be answered", dataProvider.sets.isEmpty())
        assertTrue(dataProvider.updates.isEmpty())
        assertTrue(storage.deletedUrls.isEmpty())
    }

    @Test
    fun approve_whenTheItemIsGone_throwsItemMissing() = runTest {
        val (sut, _, storage) = makeSUT(currentItem = null)
        val suggestion = makeStoredSuggestion(storage)

        try {
            sut(suggestion, "Thanks")
            fail("Expected ItemMissing")
        } catch (_: ApprovePhotoSuggestionError.ItemMissing) {
        }
    }

    @Test
    fun approve_whenTheCatalogueWriteFails_resolvesNothingAndKeepsTheFile() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeStoredSuggestion(storage)
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEMS)

        try {
            sut(suggestion, "Thanks")
        } catch (_: IOException) {
        }

        assertTrue(dataProvider.updates.isEmpty())
        assertFalse("the suggestion stays pending and can still be approved or rejected", storage.deletedUrls.contains(suggestion.photoUrl))
        assertFalse(storage.files.keys.any { it.contains("catalogPhotos") })
    }

    @Test
    fun approve_whenTheResolutionWriteFails_throwsAndKeepsTheFileForARetry() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeStoredSuggestion(storage)
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.PHOTO_SUGGESTIONS)

        try {
            sut(suggestion, "Thanks")
            fail("the sheet must stay open for a retry")
        } catch (_: IOException) {
        }

        assertFalse("a retry downloads the photo again", storage.deletedUrls.contains(suggestion.photoUrl))
    }

    @Test
    fun approve_whenTheFileDeleteFailsAfterTheResolution_doesNotThrow() = runTest {
        val (sut, dataProvider, storage) = makeSUT(currentItem = makePhotoSuggestionItem())
        val suggestion = makeStoredSuggestion(storage)
        storage.deleteError = IOException("offline")

        sut(suggestion, "Thanks")

        assertEquals("the ticket is resolved; an orphaned file is only logged", listOf(suggestion.id), dataProvider.updates.map { it.id })
    }

    @Test
    fun approve_whenCancelledWhileTheCatalogueWriteIsInFlight_stillWritesTheResolution() = runTest {
        val dataProvider = TicketDataProviderFake()
        dataProvider.currentItem = makePhotoSuggestionItem()
        val storage = StorageDataProviderFake()
        val sut = ApprovePhotoSuggestionUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = "maintainer"),
            storageProvider = storage,
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = FoodItemDTO(makePhotoSuggestionItem()).asDomain()),
            downloadFoodPhoto = DownloadFoodPhotoUseCase(storage),
            updateFoodItem = SlowUpdateFoodItemUseCase(),
        )
        val suggestion = makeStoredSuggestion(storage)

        val job = launch { sut(suggestion, "Thanks") }
        yield()
        job.cancel()
        advanceUntilIdle()

        assertEquals(
            "leaving the screen mid-approval must not leave a photo on the item with its ticket still pending",
            listOf(suggestion.id),
            dataProvider.updates.map { it.id },
        )
    }

    @Test
    fun reject_writesTheResolution_thenDeletesTheFile_andLeavesTheDocument() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeStoredSuggestion(storage)
        val sut = makeRejectSUT(dataProvider, storage)

        sut(suggestion, "Out of focus")

        val encoded = FirestoreDataMapper.encode(dataProvider.updates.first().item as TicketResolutionDTO, TicketResolutionDTO.serializer())
        assertEquals("rejected", encoded["status"])
        assertEquals("Out of focus", encoded["response"])
        assertEquals(false, encoded["hidden"])
        assertEquals(listOf(suggestion.photoUrl), storage.deletedUrls)
        assertTrue(dataProvider.deletes.isEmpty())
    }

    @Test
    fun reject_withEmptyResponse_throwsAndChangesNothing() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeStoredSuggestion(storage)
        val sut = makeRejectSUT(dataProvider, storage)

        try {
            sut(suggestion, "")
            fail("Expected ResponseRequired error")
        } catch (_: TicketResolutionError.ResponseRequired) {
        }

        assertTrue(dataProvider.updates.isEmpty())
        assertTrue(storage.deletedUrls.isEmpty())
    }

    @Test
    fun reject_whenTheFileIsAlreadyGone_stillResolves() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeStoredSuggestion(storage)
        storage.deleteError = StorageException.fromExceptionAndHttpCode(null, 404)
        val sut = makeRejectSUT(dataProvider, storage)

        sut(suggestion, "Out of focus")

        assertEquals(listOf(suggestion.id), dataProvider.updates.map { it.id })
    }

    @Test
    fun reject_whenTheResolutionWriteFails_keepsTheFile() = runTest {
        val (_, dataProvider, storage) = makeSUT(currentItem = null)
        val suggestion = makeStoredSuggestion(storage)
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.PHOTO_SUGGESTIONS)
        val sut = makeRejectSUT(dataProvider, storage)

        try {
            sut(suggestion, "Out of focus")
            fail("the sheet must stay open for a retry")
        } catch (_: IOException) {
        }

        assertTrue("deleting the file of a still pending ticket would leave an unanswerable row", storage.deletedUrls.isEmpty())
    }

    @Test
    fun reject_whenCancelledWhileTheFileDeleteIsInFlight_stillWritesTheResolution() = runTest {
        val dataProvider = TicketDataProviderFake()
        val storage = StorageDataProviderFake()
        val suggestion = makeStoredSuggestion(storage)
        val sut = RejectPhotoSuggestionUseCase(dataProvider, AuthProviderFake(userId = "maintainer"), SlowDeleteStorageDataProviderFake(storage))

        val job = launch { sut(suggestion, "Out of focus") }
        yield()
        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf(suggestion.id), dataProvider.updates.map { it.id })
    }

    // MARK: - Helpers

    private fun makeSUT(currentItem: FoodItemDomain?): Triple<ApprovePhotoSuggestionUseCase, TicketDataProviderFake, StorageDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
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

    private fun makeRejectSUT(dataProvider: TicketDataProviderFake, storage: StorageDataProviderFake) = RejectPhotoSuggestionUseCase(
        dataProvider = dataProvider,
        authProvider = AuthProviderFake(userId = "maintainer"),
        storageProvider = storage,
    )

    private suspend fun makeStoredSuggestion(storage: StorageDataProviderFake): PhotoSuggestionDomain {
        val url = storage.uploadAsync(byteArrayOf(7), "photoSuggestions/alice/12345678/ticket-1.jpg", "image/jpeg")
        return PhotoSuggestionDomain(
            id = "ticket-1",
            itemId = "12345678",
            suggestedBy = "alice",
            photoUrl = url,
            suggestedAt = Instant.now(),
            status = TicketStatus.PENDING,
            locale = "en",
        )
    }
}

private class SlowUpdateFoodItemUseCase : UpdateFoodItemUseCaseProtocol {
    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Boolean): FoodItemDomain {
        delay(1_000)
        return item
    }
}

private class SlowDeleteStorageDataProviderFake(
    private val base: StorageDataProviderFake,
) : StorageDataProviderProtocol by base {
    override suspend fun deleteAsync(url: String) {
        delay(1_000)
        base.deleteAsync(url)
    }
}
