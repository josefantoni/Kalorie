package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SaveFoodConsumedUseCaseTest {

    // MARK: - Tests

    @Test
    fun saveFoodConsumed_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeItem(), grams = 100.0, date = Instant.now(), mealTypeId = null)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun saveFoodConsumed_savesToUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(makeItem(), grams = 100.0, date = Instant.now(), mealTypeId = null)

        assertEquals("users/user-123/foodConsumed", dataProvider.setSavedCollection)
    }

    @Test
    fun saveFoodConsumed_setsDocumentIdToMatchDTOId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(), grams = 100.0, date = Instant.now(), mealTypeId = null)

        assertEquals(dataProvider.savedDTO?.id, dataProvider.setSavedId)
    }

    @Test
    fun saveFoodConsumed_writesAnUppercaseDocumentId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(), grams = 100.0, date = Instant.now(), mealTypeId = null)

        val id = dataProvider.savedDTO?.id.orEmpty()
        assertTrue("the security rules refuse a lowercase UUID id", id.isNotEmpty() && id == id.uppercase())
    }

    @Test
    fun saveFoodConsumed_stampsTheEntryWithTheGivenDateAsEpochSeconds() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(), grams = 100.0, date = Instant.ofEpochSecond(1_800_000_000), mealTypeId = null)

        assertEquals(1_800_000_000.0, dataProvider.savedDTO?.date)
    }

    @Test
    fun saveFoodConsumed_calculatesCaloriesFromWeightAndCaloriesPer100g() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(caloriesPerHundredGrams = 100.0), grams = 200.0, date = Instant.now(), mealTypeId = null)

        assertEquals(200, dataProvider.savedDTO?.calories)
    }

    @Test
    fun savesRoundedCalories_whenScalingProducesFraction() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(caloriesPerHundredGrams = 133.0), grams = 150.0, date = Instant.now(), mealTypeId = null)

        assertEquals(200, dataProvider.savedDTO?.calories)
    }

    @Test
    fun savesRoundedCalories_whenCaloriesPerHundredGramsIsFractional_roundsOnce() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(caloriesPerHundredGrams = 133.6), grams = 150.0, date = Instant.now(), mealTypeId = null)

        assertEquals(200, dataProvider.savedDTO?.calories)
    }

    @Test
    fun saveFoodConsumed_storesTheItemsCaloriesPerHundredGramsUnscaled() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(caloriesPerHundredGrams = 133.6), grams = 150.0, date = Instant.now(), mealTypeId = null)

        assertEquals(133.6, dataProvider.savedDTO?.caloriesPerHundredGrams)
    }

    @Test
    fun saveFoodConsumed_storesCorrectNameAndWeight() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(czName = "Tvaroh", engName = "Cottage cheese"), grams = 150.0, date = Instant.now(), mealTypeId = null)

        assertEquals("Tvaroh", dataProvider.savedDTO?.czName)
        assertEquals("Cottage cheese", dataProvider.savedDTO?.engName)
        assertEquals(150.0, dataProvider.savedDTO?.weight)
    }

    @Test
    fun saveFoodConsumed_scalesEnergyKJFatSaturatedAndFiberToLoggedWeight() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(fiber = 3.0), grams = 200.0, date = Instant.now(), mealTypeId = null)

        assertEquals(1296.0, dataProvider.savedDTO?.energyKJ)
        assertEquals(6.0, dataProvider.savedDTO?.fatSaturated)
        assertEquals(6.0, dataProvider.savedDTO?.fiber)
    }

    @Test
    fun saveFoodConsumed_whenItemsFiberIsUnknown_staysNilInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(fiber = null), grams = 200.0, date = Instant.now(), mealTypeId = null)

        assertNull(dataProvider.savedDTO?.fiber)
    }

    @Test
    fun saveFoodConsumed_storesTheItemsKindAsFoodItemKind() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(kind = FoodItemKind.EXTERNAL), grams = 100.0, date = Instant.now(), mealTypeId = null)

        assertEquals(FoodItemKind.EXTERNAL, dataProvider.savedDTO?.foodItemKind)
    }

    @Test
    fun saveFoodConsumed_storesTheItemsMeasure() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(measure = FoodMeasure.MILLILITRES), grams = 200.0, date = Instant.now(), mealTypeId = null)

        assertEquals(
            "a drink logged from a millilitre item must not silently read grams",
            "millilitres",
            dataProvider.savedDTO?.measureUnit,
        )
    }

    @Test
    fun saveFoodConsumed_storesTheProvidedMealTypeId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(), grams = 100.0, date = Instant.now(), mealTypeId = "lunch")

        assertEquals(
            "the use case writes whatever pin the caller resolved; it no longer resolves a window itself",
            "lunch",
            dataProvider.savedDTO?.mealTypeId,
        )
    }

    @Test
    fun saveFoodConsumed_whenMealTypeIdIsNil_storesNoPin() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeItem(), grams = 100.0, date = Instant.now(), mealTypeId = null)

        assertNull("a caller that resolved no window must be able to write no pin", dataProvider.savedDTO?.mealTypeId)
    }

    // MARK: - Helpers

    private val FirestoreDataProviderFake.savedDTO: FoodConsumedDTO?
        get() = setSavedItem as? FoodConsumedDTO

    private fun makeSUT(userId: String? = "test-user"): Pair<SaveFoodConsumedUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = SaveFoodConsumedUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }

    private fun makeItem(
        czName: String = "Vejce",
        engName: String = "Egg",
        weight: Double = 100.0,
        caloriesPerHundredGrams: Double = 155.0,
        fiber: Double? = 0.0,
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
        measure: FoodMeasure = FoodMeasure.GRAMS,
    ): FoodItemDomain = FoodItemDomain(
        id = "12345",
        kind = kind,
        czName = czName,
        engName = engName,
        weight = weight,
        date = Instant.now(),
        energyKJ = 648.0,
        caloriesPerHundredGrams = caloriesPerHundredGrams,
        fat = 10.0,
        fatSaturated = 3.0,
        fatUnsaturatedFattyAcids = 3.0,
        carbohydrate = 1.0,
        carbohydratePureSugar = 0.0,
        fiber = fiber,
        protein = 13.0,
        salt = 0.3,
        measure = measure,
    )
}
