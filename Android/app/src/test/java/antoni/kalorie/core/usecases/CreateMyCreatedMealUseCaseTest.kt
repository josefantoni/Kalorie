package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.models.MyCreatedMealValidation
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.macrokit.scaledCalories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CreateMyCreatedMealUseCaseTest {

    // MARK: - Tests

    @Test
    fun createMyCreatedMeal_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut("Kaše", listOf(makeIngredient()))
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun createMyCreatedMeal_mintsIdAndStampsCreatedAtEqualToUpdatedAt_andWritesToUserCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        val meal = sut("Kaše", listOf(makeIngredient()))

        assertFalse(meal.id.isEmpty())
        assertEquals("the rules refuse a lowercase UUID id", meal.id, meal.id.uppercase())
        assertEquals(meal.createdAt, meal.updatedAt)
        assertEquals("users/user-123/myCreatedMeals", dataProvider.setSavedCollection)
        assertEquals(meal.id, dataProvider.setSavedId)
        assertEquals(meal.id, (dataProvider.setSavedItem as MyCreatedMealDTO).id)
    }

    @Test
    fun createMyCreatedMeal_trimsName() = runTest {
        val (sut, _) = makeSUT()

        val meal = sut("  Kaše  ", listOf(makeIngredient()))

        assertEquals("Kaše", meal.name)
    }

    @Test
    fun validation_canSaveAgreesWithUseCaseAcceptance_acrossTheSameCases() = runTest {
        val cases = listOf(
            Triple("Ovesná kaše", listOf(makeIngredient()), true),
            Triple("", listOf(makeIngredient()), false),
            Triple("   ", listOf(makeIngredient()), false),
            Triple("Ovesná kaše", emptyList(), false),
            Triple("Ovesná kaše", listOf(makeIngredient(grams = 0.5)), false),
        )
        for ((name, ingredients, isValid) in cases) {
            assertEquals("canSave mismatch for '$name'", isValid, MyCreatedMealValidation.canSave(name = name, ingredients = ingredients))
            val (sut, _) = makeSUT()
            val accepted = try {
                sut(name, ingredients)
                true
            } catch (_: Exception) {
                false
            }
            assertEquals("use case and canSave disagree for '$name'", isValid, accepted)
        }
    }

    @Test
    fun asFoodItem_composesFractionalDensity_thatRoundsOnceAtLogTime() = runTest {
        val (sut, _) = makeSUT()
        val ingredients = listOf(
            makeIngredient(caloriesPerHundredGrams = 133.6, grams = 150.0),
            makeIngredient(caloriesPerHundredGrams = 90.4, grams = 50.0),
        )

        val foodItem = sut("Míchaná kaše", ingredients).asFoodItem()

        assertEquals(200.0, foodItem.weight, 0.0)
        assertEquals(122.8, foodItem.caloriesPerHundredGrams, 0.0001)
        assertEquals(246, scaledCalories(caloriesPerHundredGrams = foodItem.caloriesPerHundredGrams, ratio = foodItem.weight / 100))
    }

    @Test
    fun asFoodItem_whenAnyIngredientMissingFatSaturatedOrFiber_composedValueIsNilNotZero() = runTest {
        val (sut, _) = makeSUT()
        val ingredients = listOf(makeIngredient(fatSaturated = 3.0, fiber = 2.0), makeIngredient(fatSaturated = null, fiber = null))

        val foodItem = sut("Míchaná kaše", ingredients).asFoodItem()

        assertNull(foodItem.fatSaturated)
        assertNull(foodItem.fiber)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<CreateMyCreatedMealUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return CreateMyCreatedMealUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

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
