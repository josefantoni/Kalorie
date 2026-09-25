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

class UpdateMySubmissionUseCaseTest {

    // MARK: - Tests

    @Test
    fun update_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("sub-1", makeItem())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun update_withInvalidItem_throwsValidationErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("sub-1", makeItem(name = ""))
            fail("Expected invalidName error")
        } catch (_: FoodItemSubmissionError.InvalidName) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_whenBarcodeAlreadyInCatalogue_throwsItemAlreadyExistsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedServerDocument = FoodItemDTO(item)

        try {
            sut("sub-1", item)
            fail("Expected itemAlreadyExists error")
        } catch (_: FoodItemSubmissionError.ItemAlreadyExists) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun update_withValidItem_resetsStatusToPendingUnderTheSameSubmissionId() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")
        val item = makeItem()

        val result = sut("sub-1", item)

        assertEquals("sub-1", result.id)
        assertEquals(item.id, result.barcode)
        assertEquals("user-123", result.submittedBy)
        assertEquals(FoodItemSubmissionStatus.PENDING, result.status)
        assertNull(result.rejectReason)
        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.setSavedCollection)
        assertEquals("sub-1", dataProvider.setSavedId)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<UpdateMySubmissionUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return UpdateMySubmissionUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
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
