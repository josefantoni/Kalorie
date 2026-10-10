package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemKind
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

class UpdateFoodItemUseCaseTest {

    // MARK: - Tests

    @Test
    fun update_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _, _) = makeSUT(userId = null)
        val item = makeItem()

        try {
            sut(item, item, FoodItemFormPhoto.None)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun update_withInvalidItem_throwsValidationErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider, _) = makeSUT()
        val invalidItem = makeItem(caloriesPerHundredGrams = 0.0)

        try {
            sut(invalidItem, invalidItem, FoodItemFormPhoto.None)
            fail("Expected invalidCalories error")
        } catch (_: UpdateFoodItemError.InvalidCalories) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_withValidItem_overwritesTheCatalogueDocument() = runTest {
        val item = makeItem()
        val (sut, dataProvider, _) = makeSUT(currentDocument = item)
        val previouslyLoaded = FoodItemDTO(item).asDomain()

        sut(item, previouslyLoaded, FoodItemFormPhoto.None)

        assertEquals(Constants.Firestore.FOOD_ITEMS, dataProvider.setSavedCollection)
        assertEquals(item.id, dataProvider.setSavedId)
    }

    @Test
    fun update_whenDocumentChangedSinceLoad_throwsAndDoesNotWrite() = runTest {
        val previouslyLoaded = makeItem(caloriesPerHundredGrams = 80.0)
        val changedOnServer = makeItem(caloriesPerHundredGrams = 90.0)
        val (sut, dataProvider, _) = makeSUT(currentDocument = changedOnServer)

        try {
            sut(makeItem(caloriesPerHundredGrams = 100.0), previouslyLoaded, FoodItemFormPhoto.None)
            fail("Expected changedSinceLoad error")
        } catch (_: UpdateFoodItemError.ChangedSinceLoad) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_withoutPhoto_stillSavesSoItemsThatPredateFoodPhotosKeepWorking() = runTest {
        val item = makeItem()
        val (sut, dataProvider, storage) = makeSUT(currentDocument = item)

        sut(item, FoodItemDTO(item).asDomain(), FoodItemFormPhoto.None)

        assertEquals(item.id, dataProvider.setSavedId)
        assertTrue(storage.uploadedPaths.isEmpty())
    }

    @Test
    fun update_withLocalPhoto_uploadsToCatalogPhotosAndDeletesThePreviousCatalogueFile() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        val old = storage.uploadAsync(byteArrayOf(9), "catalogPhotos/12345678/old.jpg", "image/jpeg")
        val item = makeItem().withPhotoUrl(old)
        dataProvider.stubbedDocument = FoodItemDTO(item)

        sut(item, FoodItemDTO(item).asDomain(), FoodItemFormPhoto.Local(byteArrayOf(1, 2)))

        assertTrue(storage.uploadedPaths.last().startsWith("catalogPhotos/12345678/"))
        assertEquals(listOf(old), storage.deletedUrls)
        assertEquals(1, storage.files.size)
    }

    @Test
    fun update_whenTheWriteFails_keepsThePreviousFileAndDropsTheNewOne() = runTest {
        val (sut, dataProvider, storage) = makeSUT()
        val old = storage.uploadAsync(byteArrayOf(9), "catalogPhotos/12345678/old.jpg", "image/jpeg")
        val item = makeItem().withPhotoUrl(old)
        dataProvider.stubbedDocument = FoodItemDTO(item)
        dataProvider.stubbedSetError = RuntimeException("offline")

        try {
            sut(item, FoodItemDTO(item).asDomain(), FoodItemFormPhoto.Local(byteArrayOf(1, 2)))
        } catch (_: RuntimeException) {
        }

        assertEquals(listOf(old), storage.files.keys.toList())
    }

    // MARK: - Helpers

    private fun makeSUT(
        userId: String? = "maintainer-user",
        currentDocument: FoodItemDomain? = null,
    ): Triple<UpdateFoodItemUseCase, FirestoreDataProviderFake, StorageDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        dataProvider.stubbedDocument = currentDocument?.let { FoodItemDTO(it) }
        val storage = StorageDataProviderFake()
        val sut = UpdateFoodItemUseCase(dataProvider, AuthProviderFake(userId = userId), UploadFoodPhotoUseCase(storage), DeleteFoodPhotoUseCase(storage))
        return Triple(sut, dataProvider, storage)
    }

    private fun makeItem(id: String = "12345678", name: String = "Tvaroh", caloriesPerHundredGrams: Double = 80.0): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = name,
        engName = "Cottage cheese",
        weight = 200.0,
        date = Instant.now(),
        energyKJ = 335.0,
        caloriesPerHundredGrams = caloriesPerHundredGrams,
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
