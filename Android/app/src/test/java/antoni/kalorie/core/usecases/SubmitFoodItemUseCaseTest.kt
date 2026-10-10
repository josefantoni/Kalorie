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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class SubmitFoodItemUseCaseTest {

    // MARK: - Tests

    @Test
    fun submit_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _, _) = makeSUT(userId = null)

        try {
            sut(makeItem(), localPhoto)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun submit_withInvalidItem_throwsValidationErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider, _) = makeSUT()

        try {
            sut(makeItem(id = "123"), localPhoto)
            fail("Expected invalidCode error")
        } catch (_: FoodItemSubmissionError.InvalidCode) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submit_whenBarcodeAlreadyInCatalogue_throwsItemAlreadyExistsAndDoesNotWrite() = runTest {
        val (sut, dataProvider, _) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedServerDocument = FoodItemDTO(item)

        try {
            sut(item, localPhoto)
            fail("Expected itemAlreadyExists error")
        } catch (_: FoodItemSubmissionError.ItemAlreadyExists) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submit_withValidItem_writesPendingSubmissionToFoodItemSubmissions() = runTest {
        val (sut, dataProvider, _) = makeSUT(userId = "user-123")
        val item = makeItem()

        val result = sut(item, localPhoto)

        assertEquals("the item already has a real barcode; it must be kept, not discarded", item.id, result.barcode)
        assertEquals("user-123", result.submittedBy)
        assertEquals(FoodItemSubmissionStatus.PENDING, result.status)
        assertNull(result.rejectReason)
        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.setSavedCollection)
        assertEquals(result.id, dataProvider.setSavedId)
        assertEquals("the rules refuse a lowercase UUID id", result.id, result.id.uppercase())
    }

    @Test
    fun submit_withEmptyId_usesTheGeneratedSubmissionIdAsTheItemsIdentityAndHasNoBarcode() = runTest {
        val (sut, _, _) = makeSUT(userId = "user-123")

        val result = sut(makeItem(id = "", name = "Kukuřice"), localPhoto)

        assertEquals(
            "a barcode-less item's identity is the submission's own id, so favourites, portions and entries keep pointing at the right document once approved",
            result.id,
            result.item.id,
        )
        assertNull("the id is a UUID, not a barcode, and must never be sent to OpenFoodFacts or shown as one", result.barcode)
    }

    @Test
    fun submit_withoutPhoto_throwsPhotoMissingAndUploadsAndWritesNothing() = runTest {
        val (sut, dataProvider, storage) = makeSUT()

        try {
            sut(makeItem(), FoodItemFormPhoto.None)
            fail("Expected photoMissing error")
        } catch (_: FoodItemSubmissionError.PhotoMissing) {
        }

        assertTrue(storage.uploadedPaths.isEmpty())
        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submit_withLocalPhoto_uploadsToTheAuthorsFolderAndStoresItsUrlOnTheItem() = runTest {
        val (sut, _, storage) = makeSUT(userId = "user-123")

        val result = sut(makeItem(), localPhoto)

        val path = storage.uploadedPaths.first()
        assertTrue("storage rules only let the author write into their own folder", path.startsWith("submissionPhotos/user-123/"))
        assertEquals(storage.files.keys.first(), result.item.photoUrl)
    }

    @Test
    fun submit_withRemotePhoto_keepsItAndUploadsNothing() = runTest {
        val (sut, _, storage) = makeSUT()

        val result = sut(makeItem(), FoodItemFormPhoto.Remote("https://storage.fake/existing.jpg"))

        assertEquals("https://storage.fake/existing.jpg", result.item.photoUrl)
        assertTrue(storage.uploadedPaths.isEmpty())
    }

    @Test
    fun submit_whenTheWriteFails_deletesTheUploadedPhoto() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        dataProvider.stubbedSetError = RuntimeException("offline")

        try {
            sut(makeItem(), localPhoto)
            fail("Expected the write error")
        } catch (error: RuntimeException) {
            assertEquals("offline", error.message)
        }

        assertTrue("a failed submission must not leave a file nobody points at", storage.files.isEmpty())
    }

    @Test
    fun submit_whenTheUploadFails_throwsPhotoUploadFailedAndWritesNothing() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        storage.uploadError = RuntimeException("offline")

        try {
            sut(makeItem(), localPhoto)
            fail("Expected photoUploadFailed error")
        } catch (_: FoodItemSubmissionError.PhotoUploadFailed) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submit_whenTheBarcodeAlreadyExists_uploadsNothing() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedServerDocument = FoodItemDTO(item)

        try {
            sut(item, localPhoto)
        } catch (_: FoodItemSubmissionError.ItemAlreadyExists) {
        }

        assertTrue(storage.uploadedPaths.isEmpty())
    }

    // MARK: - Helpers

    private val localPhoto = FoodItemFormPhoto.Local(byteArrayOf(1, 2, 3))

    private fun makeSUT(userId: String? = "test-user"): Triple<SubmitFoodItemUseCase, FirestoreDataProviderFake, StorageDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val storage = StorageDataProviderFake()
        val sut = SubmitFoodItemUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = userId),
            uploadFoodPhoto = UploadFoodPhotoUseCase(storage),
            deleteFoodPhoto = DeleteFoodPhotoUseCase(storage),
        )
        return Triple(sut, dataProvider, storage)
    }

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
