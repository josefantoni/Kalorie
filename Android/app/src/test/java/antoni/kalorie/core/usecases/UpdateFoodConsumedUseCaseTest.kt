package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.networking.FirestoreDataProviderError
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedWeightUpdateDTO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class UpdateFoodConsumedUseCaseTest {

    // MARK: - Tests

    @Test
    fun updateFoodConsumed_writesOnlyWeightAndScaledMacros_soAPropagatedNameIsNotReverted() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(weight = 100.0), newWeight = 200.0)

        val keys = Json.encodeToJsonElement(FoodConsumedWeightUpdateDTO.serializer(), dataProvider.savedDTO!!).jsonObject.keys
        assertEquals(
            "the in-memory name can be older than a propagated rename, so an edit must not write cz_name/eng_name back",
            setOf(
                "weight", "calories", "calories_per_hundred_grams", "energy_kj", "protein", "carbohydrate",
                "carbohydrate_sugar", "fat", "fat_saturated", "fat_unsaturated", "fiber", "salt",
            ),
            keys,
        )
    }

    @Test
    fun updateFoodConsumed_updatesInsteadOfOverwritingTheDocument() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(), newWeight = 200.0)

        assertNull(dataProvider.setSavedItem)
    }

    @Test
    fun updateFoodConsumed_writesToUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(makeFood(), newWeight = 200.0)

        assertEquals("users/user-123/foodConsumed", dataProvider.updatedCollection)
        assertEquals("1", dataProvider.updatedId)
    }

    @Test
    fun updateFoodConsumed_whenTheEntryWasDeletedMeanwhile_propagatesNotFoundAndWritesNoDocument() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedUpdateError = FirestoreDataProviderError.NotFound

        try {
            sut(makeFood(), newWeight = 200.0)
            fail("Expected NotFound error")
        } catch (_: FirestoreDataProviderError.NotFound) {
        }

        assertNull("a weight edit of a deleted entry must not recreate a document with only the weight and macros", dataProvider.setSavedItem)
        assertNull(dataProvider.mergedItem)
    }

    @Test
    fun updateFoodConsumed_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeFood(), newWeight = 200.0)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun updateFoodConsumed_recalculatesCaloriesFromStoredPerHundredGramBasis_avoidingCompoundedRounding() = runTest {
        val (sut, dataProvider) = makeSUT()

        // 1g of a 33 kcal/100g food logs to 0 kcal (rounds down). Rescaling that rounded 0 by
        // newWeight/oldWeight would stay 0 forever; rescaling from the stored per-100g basis does not.
        sut(makeFood(weight = 1.0, calories = 0, caloriesPerHundredGrams = 33.0), newWeight = 100.0)

        assertEquals(33, dataProvider.savedDTO?.calories)
    }

    @Test
    fun updateFoodConsumed_preservesCaloriesPerHundredGrams() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(weight = 100.0, caloriesPerHundredGrams = 155.0), newWeight = 200.0)

        assertEquals(155.0, dataProvider.savedDTO?.caloriesPerHundredGrams)
    }

    @Test
    fun updateFoodConsumed_whenWeightChanges_scalesEnergyKJFatSaturatedAndFiber() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(weight = 100.0, energyKJ = 200.0, fatSaturated = 4.0, fiber = 2.0), newWeight = 200.0)

        assertEquals(400.0, dataProvider.savedDTO?.energyKJ)
        assertEquals(8.0, dataProvider.savedDTO?.fatSaturated)
        assertEquals(4.0, dataProvider.savedDTO?.fiber)
    }

    @Test
    fun updateFoodConsumed_whenFoodsSaturatedFatIsUnknown_staysNilInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(weight = 100.0, fatSaturated = null), newWeight = 200.0)

        assertNull(dataProvider.savedDTO?.fatSaturated)
    }

    @Test
    fun updateFoodConsumed_whenFoodsFiberIsUnknown_staysNilInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(weight = 100.0, fiber = null), newWeight = 200.0)

        assertNull(dataProvider.savedDTO?.fiber)
    }

    @Test
    fun updateFoodConsumed_whenExistingWeightIsNotPositive_throwsInvalidWeightError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(makeFood(weight = 0.0), newWeight = 200.0)
            fail("Expected invalidWeight error")
        } catch (_: UpdateFoodConsumedError.InvalidWeight) {
        }
    }

    // MARK: - Helpers

    private val FirestoreDataProviderFake.savedDTO: FoodConsumedWeightUpdateDTO?
        get() = updatedItem as? FoodConsumedWeightUpdateDTO

    private fun makeSUT(userId: String? = "test-user"): Pair<UpdateFoodConsumedUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = UpdateFoodConsumedUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }

    private fun makeFood(
        foodItemId: String = "12345",
        weight: Double = 100.0,
        calories: Int = 155,
        caloriesPerHundredGrams: Double = 155.0,
        energyKJ: Double = 649.0,
        fatSaturated: Double? = 3.0,
        fiber: Double? = 0.0,
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
        mealTypeId: String? = null,
        measure: FoodMeasure = FoodMeasure.GRAMS,
    ): FoodConsumedDomain = FoodConsumedDomain(
        id = "1",
        foodItemId = foodItemId,
        foodItemKind = kind,
        czName = "Vejce",
        engName = "Egg",
        weight = weight,
        date = Instant.now(),
        calories = calories,
        caloriesPerHundredGrams = caloriesPerHundredGrams,
        energyKJ = energyKJ,
        protein = 13.0,
        carbohydrate = 1.0,
        carbohydrateSugar = 0.0,
        fat = 10.0,
        fatSaturated = fatSaturated,
        fatUnsaturated = 3.0,
        fiber = fiber,
        salt = 0.3,
        mealTypeId = mealTypeId,
        measure = measure,
    )
}
