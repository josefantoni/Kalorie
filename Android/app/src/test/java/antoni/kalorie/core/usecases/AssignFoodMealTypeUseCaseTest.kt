package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AssignFoodMealTypeUseCaseTest {

    // MARK: - Tests

    @Test
    fun assignFoodMealType_setsMealTypeId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(), mealTypeId = "lunch")

        assertEquals("lunch", dataProvider.savedDTO?.mealTypeId)
    }

    @Test
    fun assignFoodMealType_writesToUserSpecificCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(makeFood(), mealTypeId = "lunch")

        assertEquals("users/user-123/foodConsumed", dataProvider.setSavedCollection)
    }

    @Test
    fun assignFoodMealType_preservesFoodItemId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(foodItemId = "12345"), mealTypeId = "lunch")

        assertEquals(
            "setAsync overwrites the whole document, so a writer that drops a field would silently corrupt it while only meaning to move the meal-type pin",
            "12345",
            dataProvider.savedDTO?.foodItemId,
        )
    }

    @Test
    fun assignFoodMealType_preservesWeightAndCalories() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(weight = 150.0, calories = 200), mealTypeId = "lunch")

        assertEquals(150.0, dataProvider.savedDTO?.weight)
        assertEquals(200, dataProvider.savedDTO?.calories)
    }

    @Test
    fun assignFoodMealType_whenFoodsFiberIsUnknown_staysNilInsteadOfZero() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(makeFood(fiber = null), mealTypeId = "lunch")

        assertNull(dataProvider.savedDTO?.fiber)
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

    private val FirestoreDataProviderFake.savedDTO: FoodConsumedDTO?
        get() = setSavedItem as? FoodConsumedDTO

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
