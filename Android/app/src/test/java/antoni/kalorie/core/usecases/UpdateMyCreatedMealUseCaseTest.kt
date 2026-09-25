package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealError
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MyCreatedMealDTO
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class UpdateMyCreatedMealUseCaseTest {

    // MARK: - Tests

    @Test
    fun updateMyCreatedMeal_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut(makeMeal())
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun updateMyCreatedMeal_whenInvalid_throwsWithoutWriting() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut(makeMeal(name = "", ingredients = emptyList()))
            fail("Expected validation error")
        } catch (_: MyCreatedMealError) {
        }

        assertNull(dataProvider.setSavedCollection)
    }

    @Test
    fun updateMyCreatedMeal_preservesCreatedAt_whileStampingUpdatedAt() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")
        val originalCreatedAt = Instant.ofEpochSecond(1_000)
        val meal = makeMeal(createdAt = originalCreatedAt, updatedAt = originalCreatedAt)

        sut(meal)

        val savedDTO = dataProvider.setSavedItem as MyCreatedMealDTO
        assertEquals(1_000.0, savedDTO.createdAt, 0.0)
        assertNotEquals(1_000.0, savedDTO.updatedAt, 0.0)
        assertEquals("users/user-123/myCreatedMeals", dataProvider.setSavedCollection)
        assertEquals(meal.id, dataProvider.setSavedId)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<UpdateMyCreatedMealUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return UpdateMyCreatedMealUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeMeal(
        name: String = "Kaše",
        ingredients: List<MyCreatedMealIngredientDomain> = listOf(makeIngredient()),
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
    ): MyCreatedMealDomain = MyCreatedMealDomain(id = "meal-1", name = name, ingredients = ingredients, createdAt = createdAt, updatedAt = updatedAt)

    private fun makeIngredient(
        caloriesPerHundredGrams: Double = 155.0,
        grams: Double = 50.0,
        fatSaturated: Double? = 3.0,
        fiber: Double? = 0.0,
    ): MyCreatedMealIngredientDomain = MyCreatedMealIngredientDomain(
        foodItemId = "12345",
        czName = "Ovesné vločky",
        engName = "Oats",
        grams = grams,
        nutrition = FoodNutritionValues(
            energyKJ = 648.0,
            caloriesPerHundredGrams = caloriesPerHundredGrams,
            fat = 10.0,
            fatSaturated = fatSaturated,
            fatUnsaturatedFattyAcids = 3.0,
            carbohydrate = 1.0,
            carbohydratePureSugar = 0.0,
            fiber = fiber,
            protein = 13.0,
            salt = 0.3,
        ),
    )
}
