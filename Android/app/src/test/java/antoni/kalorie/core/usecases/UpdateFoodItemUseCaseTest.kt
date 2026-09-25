package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class UpdateFoodItemUseCaseTest {

    // MARK: - Tests

    @Test
    fun update_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)
        val item = makeItem()

        try {
            sut(item, item)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun update_withInvalidItem_throwsValidationErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()
        val invalidItem = makeItem(caloriesPerHundredGrams = 0.0)

        try {
            sut(invalidItem, invalidItem)
            fail("Expected invalidCalories error")
        } catch (_: UpdateFoodItemError.InvalidCalories) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_withValidItem_overwritesTheCatalogueDocument() = runTest {
        val item = makeItem()
        val (sut, dataProvider) = makeSUT(currentDocument = item)
        val previouslyLoaded = FoodItemDTO(item).asDomain()

        sut(item, previouslyLoaded)

        assertEquals(Constants.Firestore.FOOD_ITEMS, dataProvider.setSavedCollection)
        assertEquals(item.id, dataProvider.setSavedId)
    }

    @Test
    fun update_whenDocumentChangedSinceLoad_throwsAndDoesNotWrite() = runTest {
        val previouslyLoaded = makeItem(caloriesPerHundredGrams = 80.0)
        val changedOnServer = makeItem(caloriesPerHundredGrams = 90.0)
        val (sut, dataProvider) = makeSUT(currentDocument = changedOnServer)

        try {
            sut(makeItem(caloriesPerHundredGrams = 100.0), previouslyLoaded)
            fail("Expected changedSinceLoad error")
        } catch (_: UpdateFoodItemError.ChangedSinceLoad) {
        }

        assertNull(dataProvider.setSavedId)
    }

    // MARK: - Helpers

    private fun makeSUT(
        userId: String? = "maintainer-user",
        currentDocument: FoodItemDomain? = null,
    ): Pair<UpdateFoodItemUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        dataProvider.stubbedDocument = currentDocument?.let { FoodItemDTO(it) }
        return UpdateFoodItemUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
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
