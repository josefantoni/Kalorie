package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionError
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SubmitFoodItemUseCaseTest {

    // MARK: - Tests

    @Test
    fun submit_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeItem())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun submit_withInvalidItem_throwsValidationErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut(makeItem(id = "123"))
            fail("Expected invalidCode error")
        } catch (_: FoodItemSubmissionError.InvalidCode) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submit_whenBarcodeAlreadyInCatalogue_throwsItemAlreadyExistsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedServerDocument = FoodItemDTO(item)

        try {
            sut(item)
            fail("Expected itemAlreadyExists error")
        } catch (_: FoodItemSubmissionError.ItemAlreadyExists) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun submit_withValidItem_writesPendingSubmissionToFoodItemSubmissions() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")
        val item = makeItem()

        val result = sut(item)

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
        val (sut, _) = makeSUT(userId = "user-123")

        val result = sut(makeItem(id = "", name = "Kukuřice"))

        assertEquals(
            "a barcode-less item's identity is the submission's own id, so favourites, portions and entries keep pointing at the right document once approved",
            result.id,
            result.item.id,
        )
        assertNull("the id is a UUID, not a barcode, and must never be sent to OpenFoodFacts or shown as one", result.barcode)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<SubmitFoodItemUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return SubmitFoodItemUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
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
