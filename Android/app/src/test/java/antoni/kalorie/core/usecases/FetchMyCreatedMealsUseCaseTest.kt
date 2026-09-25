package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MyCreatedMealDTO
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FetchMyCreatedMealsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchMyCreatedMeals_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchMyCreatedMeals_queriesUserSpecificCollectionOrderedByUpdatedAtDescending() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut()

        assertEquals("users/user-123/myCreatedMeals", dataProvider.queriedCollection)
        assertEquals("updated_at", dataProvider.queriedOrderByField)
        assertEquals(true, dataProvider.queriedDescending)
        assertEquals(50, dataProvider.queriedLimit)
    }

    @Test
    fun fetchMyCreatedMeals_mapsStubbedDTOsToDomains() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(makeDTO(id = "meal-1", name = "Kaše"))

        val result = sut()

        assertEquals(listOf("meal-1"), result.map { it.id })
        assertEquals(listOf("Kaše"), result.map { it.name })
    }

    @Test
    fun fetchMyCreatedMeals_whenIngredientFatSaturatedAndFiberMissingFromStoredDocument_stayNullInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()
        val stored = FirestoreDataMapper.encode(makeDTO(id = "meal-1", name = "Kaše"), MyCreatedMealDTO.serializer())
        @Suppress("UNCHECKED_CAST")
        val ingredients = (stored["ingredients"] as List<Map<String, Any?>>).map { it - "fat_saturated" - "fiber" }
        dataProvider.stubbedDocuments = listOf(
            FirestoreDataMapper.decode(stored + ("ingredients" to ingredients), MyCreatedMealDTO.serializer()),
        )

        val result = sut()

        assertNull(result.first().ingredients.first().nutrition.fatSaturated)
        assertNull(result.first().ingredients.first().nutrition.fiber)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchMyCreatedMealsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchMyCreatedMealsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeDTO(id: String, name: String): MyCreatedMealDTO =
        MyCreatedMealDTO(MyCreatedMealDomain(id = id, name = name, ingredients = listOf(makeIngredient()), createdAt = Instant.now(), updatedAt = Instant.now()))

    private fun makeIngredient(): MyCreatedMealIngredientDomain = MyCreatedMealIngredientDomain(
        foodItemId = "12345",
        czName = "Ovesné vločky",
        engName = "Oats",
        grams = 50.0,
        nutrition = FoodNutritionValues(
            energyKJ = 648.0,
            caloriesPerHundredGrams = 155.0,
            fat = 10.0,
            fatSaturated = 3.0,
            fatUnsaturatedFattyAcids = 3.0,
            carbohydrate = 1.0,
            carbohydratePureSugar = 0.0,
            fiber = 0.0,
            protein = 13.0,
            salt = 0.3,
        ),
    )
}
