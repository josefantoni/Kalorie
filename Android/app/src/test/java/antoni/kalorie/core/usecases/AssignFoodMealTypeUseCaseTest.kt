package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataProviderError
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedMealTypeUpdateDTO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class AssignFoodMealTypeUseCaseTest {

    // MARK: - Tests

    @Test
    fun assignFoodMealType_writesOnlyTheMealTypeId_soAPropagatedNameIsNotReverted() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(), mealTypeId = "lunch")

        val updated = dataProvider.updatedItem
        val keys = updated?.let { Json.encodeToJsonElement(serializerFor(it), it).jsonObject }
        assertEquals(
            "the in-memory name can be older than a propagated rename, so pinning a meal type must not write cz_name/eng_name back",
            mapOf("meal_type_id" to "lunch"),
            keys?.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun assignFoodMealType_updatesInsteadOfOverwritingTheDocument() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(makeFood(), mealTypeId = "lunch")

        assertEquals("users/user-123/foodConsumed", dataProvider.updatedCollection)
        assertEquals("1", dataProvider.updatedId)
        assertNull(dataProvider.setSavedItem)
    }

    @Test
    fun assignFoodMealType_whenTheEntryWasDeletedMeanwhile_propagatesNotFoundAndWritesNoDocument() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedUpdateError = FirestoreDataProviderError.NotFound

        try {
            sut(makeFood(), mealTypeId = "lunch")
            fail("Expected NotFound error")
        } catch (_: FirestoreDataProviderError.NotFound) {
        }

        assertNull(dataProvider.setSavedItem)
        assertNull(dataProvider.updatedItem)
    }

    @Test
    fun assignFoodMealType_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeFood(), mealTypeId = "lunch")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    // MARK: - Helpers

    @Suppress("UNCHECKED_CAST")
    private fun serializerFor(item: Any): KSerializer<Any> = when (item) {
        is FoodConsumedMealTypeUpdateDTO -> FoodConsumedMealTypeUpdateDTO.serializer()
        else -> error("Unexpected update payload: $item")
    } as KSerializer<Any>

    private fun makeSUT(userId: String? = "test-user"): Pair<AssignFoodMealTypeUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = AssignFoodMealTypeUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }

    private fun makeFood(
        foodItemId: String = "12345",
        weight: Double = 100.0,
        calories: Int = 155,
        fiber: Double? = 0.0,
    ): FoodConsumedDomain = FoodConsumedDomain(
        id = "1",
        foodItemId = foodItemId,
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = "Vejce",
        engName = "Egg",
        weight = weight,
        date = Instant.now(),
        calories = calories,
        caloriesPerHundredGrams = 155.0,
        energyKJ = 649.0,
        protein = 13.0,
        carbohydrate = 1.0,
        carbohydrateSugar = 0.0,
        fat = 10.0,
        fatSaturated = 3.0,
        fatUnsaturated = 3.0,
        fiber = fiber,
        salt = 0.3,
        mealTypeId = null,
    )
}
