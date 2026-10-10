package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionError
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.StorageDataProviderFake
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class UpdateMySubmissionUseCaseTest {

    // MARK: - Tests

    @Test
    fun update_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _, _) = makeSUT(userId = null)

        try {
            sut("sub-1", makeItem(), localPhoto, null)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun update_withInvalidItem_throwsValidationErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider, _) = makeSUT()

        try {
            sut("sub-1", makeItem(name = ""), localPhoto, null)
            fail("Expected invalidName error")
        } catch (_: FoodItemSubmissionError.InvalidName) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_whenBarcodeAlreadyInCatalogue_throwsItemAlreadyExistsAndDoesNotWrite() = runTest {
        val (sut, dataProvider, _) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedServerDocument = FoodItemDTO(item)

        try {
            sut("sub-1", item, localPhoto, null)
            fail("Expected itemAlreadyExists error")
        } catch (_: FoodItemSubmissionError.ItemAlreadyExists) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_withValidItem_resetsStatusToPendingUnderTheSameSubmissionId() = runTest {
        val (sut, dataProvider, _) = makeSUT(userId = "user-123")
        val item = makeItem()

        val result = sut("sub-1", item, localPhoto, null)

        assertEquals("sub-1", result.id)
        assertEquals(item.id, result.barcode)
        assertEquals("user-123", result.submittedBy)
        assertEquals(FoodItemSubmissionStatus.PENDING, result.status)
        assertNull(result.rejectReason)
        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.setSavedCollection)
        assertEquals("sub-1", dataProvider.setSavedId)
    }

    @Test
    fun update_withoutPhoto_throwsPhotoMissingSoAnOldPhotolessSubmissionMustGainOne() = runTest {
        val (sut, dataProvider, _) = makeSUT()

        try {
            sut("sub-1", makeItem(), FoodItemFormPhoto.None, null)
            fail("Expected photoMissing error")
        } catch (_: FoodItemSubmissionError.PhotoMissing) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_withReplacedPhoto_writesFirstThenDeletesTheOldFile() = runTest {
        val (sut, _, storage) = makeSUT()
        val old = storage.seedOldPhoto()

        val result = sut("sub-1", makeItem(), localPhoto, old)

        assertEquals(listOf(old), storage.deletedUrls)
        assertNotEquals(old, result.item.photoUrl)
        assertNotNull(storage.files[result.item.photoUrl])
    }

    @Test
    fun update_withUnchangedRemotePhoto_touchesNoStorage() = runTest {
        val (sut, _, storage) = makeSUT()
        val old = storage.seedOldPhoto()

        sut("sub-1", makeItem(), FoodItemFormPhoto.Remote(old), old)

        assertTrue("the author's only copy must survive when the photo was not replaced", storage.deletedUrls.isEmpty())
        assertEquals(1, storage.uploadedPaths.size)
    }

    @Test
    fun update_whenTheWriteFails_keepsTheOldFileAndDropsTheNewOne() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        val old = storage.seedOldPhoto()
        dataProvider.stubbedSetError = RuntimeException("offline")

        try {
            sut("sub-1", makeItem(), localPhoto, old)
        } catch (_: RuntimeException) {
        }

        assertEquals(listOf(old), storage.files.keys.toList())
    }

    // MARK: - Helpers

    private val localPhoto = FoodItemFormPhoto.Local(byteArrayOf(1, 2, 3))

    private fun makeSUT(userId: String? = "test-user"): Triple<UpdateMySubmissionUseCase, FirestoreDataProviderFake, StorageDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val storage = StorageDataProviderFake()
        val sut = UpdateMySubmissionUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = userId),
            uploadFoodPhoto = UploadFoodPhotoUseCase(storage),
            deleteFoodPhoto = DeleteFoodPhotoUseCase(storage),
        )
        return Triple(sut, dataProvider, storage)
    }

    private suspend fun StorageDataProviderFake.seedOldPhoto(): String = uploadAsync(byteArrayOf(9), "submissionPhotos/test-user/old.jpg", "image/jpeg")

    private fun makeItem(id: String = "12345678", name: String = "Tvaroh"): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = name,
        engName = "Cottage cheese",
        weight = 200.0,
        date = Instant.now(),
        energyKJ = 335.0,
        caloriesPerHundredGrams = 80.0,
        fat = 0.5,
        fatSaturated = 0.3,
        fatUnsaturatedFattyAcids = 0.2,
        carbohydrate = 4.0,
        carbohydratePureSugar = 3.0,
        fiber = 0.0,
        protein = 13.0,
        salt = 0.1,
    )
}
