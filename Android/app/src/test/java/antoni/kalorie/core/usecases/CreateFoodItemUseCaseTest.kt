package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataProviderError
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemDTO
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CreateFoodItemUseCaseTest {

    // MARK: - Tests

    @Test
    fun createFoodItem_withInvalidCode_throwsInvalidCodeError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(makeItem(id = ""))
            fail("Expected invalidCode error")
        } catch (_: CreateFoodItemError.InvalidCode) {
        }
    }

    @Test
    fun createFoodItem_withCodeOfInvalidLength_throwsInvalidCodeError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(makeItem(id = "123456789"))
            fail("Expected invalidCode error")
        } catch (_: CreateFoodItemError.InvalidCode) {
        }
    }

    @Test
    fun createFoodItem_withNonASCIIDigits_throwsInvalidCodeError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(makeItem(id = "١٢٣٤٥٦٧٨"))
            fail("Expected invalidCode error")
        } catch (_: CreateFoodItemError.InvalidCode) {
        }
    }

    @Test
    fun createFoodItem_withEmptyName_throwsInvalidNameError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(makeItem(name = ""))
            fail("Expected invalidName error")
        } catch (_: CreateFoodItemError.InvalidName) {
        }
    }

    @Test
    fun createFoodItem_withZeroCalories_throwsInvalidCaloriesError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(makeItem(caloriesPerHundredGrams = 0.0))
            fail("Expected invalidCalories error")
        } catch (_: CreateFoodItemError.InvalidCalories) {
        }
    }

    @Test
    fun createFoodItem_withValidInput_returnsNewFoodItem() = runTest {
        val (sut, dataProvider) = makeSUT()
        val item = makeItem()

        val result = sut(item)

        assertEquals(item.czName, result.czName)
        assertEquals(item.caloriesPerHundredGrams, result.caloriesPerHundredGrams, 0.0)
        assertEquals(item.id, dataProvider.setSavedId)
    }

    @Test
    fun createFoodItem_whenItemAlreadyExists_throwsItemAlreadyExistsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedServerDocument = FoodItemDTO(item)

        try {
            sut(item)
            fail("Expected itemAlreadyExists error")
        } catch (_: CreateFoodItemError.ItemAlreadyExists) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun createFoodItem_whenServerUnreachable_throwsUnreachableAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedError = FirestoreDataProviderError.Unreachable

        try {
            sut(makeItem())
            fail("Expected unreachable error")
        } catch (_: FirestoreDataProviderError.Unreachable) {
        }

        assertNull(dataProvider.setSavedId)
    }

    @Test
    fun createFoodItem_whenWriteIsRejectedByRules_andReReadConfirmsDuplicate_throwsItemAlreadyExists() = runTest {
        val (sut, dataProvider) = makeSUT()
        val item = makeItem()
        dataProvider.stubbedSetError = permissionDenied()
        dataProvider.stubbedServerDocumentSequence = listOf(null, FoodItemDTO(item))

        try {
            sut(item)
            fail("Expected itemAlreadyExists error")
        } catch (_: CreateFoodItemError.ItemAlreadyExists) {
        }
    }

    @Test
    fun createFoodItem_whenWriteIsRejectedByRules_andReReadFindsNothing_rethrowsOriginalError() = runTest {
        val (sut, dataProvider) = makeSUT()
        val deniedError = permissionDenied()
        dataProvider.stubbedSetError = deniedError

        try {
            sut(makeItem())
            fail("Expected the original permissionDenied error")
        } catch (_: CreateFoodItemError.ItemAlreadyExists) {
            fail("Should not relabel the failure without confirming a duplicate exists")
        } catch (error: FirebaseFirestoreException) {
            assertSame(deniedError, error)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<CreateFoodItemUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return CreateFoodItemUseCase(dataProvider) to dataProvider
    }

    private fun permissionDenied() = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

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
