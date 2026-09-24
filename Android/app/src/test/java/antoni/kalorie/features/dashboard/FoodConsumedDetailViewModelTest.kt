package antoni.kalorie.features.dashboard

import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseFake
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.UpdateFoodConsumedUseCaseProtocol
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodConsumedDetailViewModelTest {

    // MARK: - onMealTypeSelected (staging only, no write)

    @Test
    fun onMealTypeSelected_stagesTheSelectionWithoutWritingOrEnablingSaveAlone() {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        val food = makeFood(date = makeDate(hour = 14, minute = 0))
        val sut = makeSUT(food = food, mealTypes = listOf(breakfast))
        assertNull(
            "the food's own time falls outside the breakfast window, so nothing should resolve before a pick is made",
            sut.mealTypeId.value,
        )

        sut.onMealTypeSelected("breakfast")

        assertEquals("the picker must reflect the pick immediately, even before Save is tapped", "breakfast", sut.mealTypeId.value)
        assertNull(
            "selecting a meal type must not write anything on its own — only onSave() persists it",
            sut.food.mealTypeId,
        )
        assertTrue("Save must become enabled the moment a different meal type is picked", sut.hasChanges)
    }

    @Test
    fun onMealTypeSelected_reselectingTheAlreadyPinnedValue_doesNotEnableSave() {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        val sut = makeSUT(food = makeFood(mealTypeId = "breakfast"), mealTypes = listOf(breakfast))

        sut.onMealTypeSelected("breakfast")

        assertFalse("picking the value that is already pinned is not a pending change", sut.hasChanges)
    }

    // MARK: - onSave — meal type pin

    @Test
    fun onSave_whenOnlyMealTypeWasSelected_writesThePinWithoutTouchingWeight() = runTest {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        var didNotify = false
        val sut = makeSUT(mealTypes = listOf(breakfast), onFoodUpdated = { didNotify = true })
        sut.onMealTypeSelected("breakfast")

        sut.onSave()

        assertEquals("breakfast", sut.food.mealTypeId)
        assertTrue("the Dashboard's cache must be invalidated so the entry moves section", didNotify)
        assertNull(sut.alertItem.value)
        assertFalse("a successful save must clear the pending state", sut.hasChanges)
    }

    @Test
    fun onSave_whenNothingWasChanged_doesNothing() = runTest {
        val sut = makeSUT()

        sut.onSave()

        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSave_whenAssignFails_leavesMealTypeIdUnchangedAndShowsAlert() = runTest {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        val sut = makeSUT(mealTypes = listOf(breakfast), assignFoodMealType = AssignFoodMealTypeUseCaseFake(shouldThrow = true))
        sut.onMealTypeSelected("breakfast")

        sut.onSave()

        assertNull("a failed write must not optimistically move the entry to a section it was never saved into", sut.food.mealTypeId)
        assertNotNull(sut.alertItem.value)
        assertTrue("a failed save must leave Save enabled so the user can retry", sut.hasChanges)
    }

    @Test
    fun onSave_whenMealTypeWasDeletedSinceScreenOpened_refetchesAndBlocksWithAlertInsteadOfWritingADanglingId() = runTest {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        val food = makeFood(mealTypeId = "lunch")
        val sut = makeSUT(
            food = food,
            mealTypes = listOf(breakfast),
            fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = emptyList()),
        )
        sut.onMealTypeSelected("breakfast")

        sut.onSave()

        assertTrue(
            "the screen must pick up that breakfast was deleted elsewhere instead of trusting its initial snapshot",
            sut.mealTypes.value.isEmpty(),
        )
        assertEquals("an id that no longer exists must never overwrite the food's real pin", "lunch", sut.food.mealTypeId)
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onSave_whenSelectionMatchesTimeResolvedButUnpinnedMealType_stillCreatesPin() = runTest {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 0, endMinutes = 1439)
        val food = makeFood(mealTypeId = null)
        var didNotify = false
        val sut = makeSUT(food = food, mealTypes = listOf(breakfast), onFoodUpdated = { didNotify = true })
        assertEquals(
            "the entry already displays under breakfast by time alone, before any pin exists",
            "breakfast",
            sut.mealTypeId.value,
        )

        sut.onMealTypeSelected("breakfast")
        sut.onSave()

        assertEquals(
            "confirming the meal type the entry already resolves to by time must still create an explicit pin",
            "breakfast",
            sut.food.mealTypeId,
        )
        assertTrue(didNotify)
    }

    @Test
    fun onSave_whenBothWeightAndMealTypeChanged_writesBoth() = runTest {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        val sut = makeSUT(mealTypes = listOf(breakfast))
        sut.weight.value = 150.0
        sut.onMealTypeSelected("breakfast")

        sut.onSave()

        assertEquals(150.0, sut.food.weight, 0.0)
        assertEquals("breakfast", sut.food.mealTypeId)
        assertFalse(sut.hasChanges)
        assertNull(sut.alertItem.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        food: FoodConsumedDomain? = null,
        mealTypes: List<MealTypeDomain> = emptyList(),
        updateFoodConsumed: UpdateFoodConsumedUseCaseProtocol = UpdateFoodConsumedUseCaseFake(),
        assignFoodMealType: AssignFoodMealTypeUseCaseProtocol = AssignFoodMealTypeUseCaseFake(),
        fetchMealTypes: FetchMealTypesUseCaseProtocol? = null,
        onFoodUpdated: () -> Unit = {},
    ): FoodConsumedDetailViewModel =
        FoodConsumedDetailViewModel(
            food = food ?: makeFood(),
            mealTypes = mealTypes,
            updateFoodConsumed = updateFoodConsumed,
            assignFoodMealType = assignFoodMealType,
            fetchMealTypes = fetchMealTypes ?: FetchMealTypesUseCaseFake(stubbedTypes = mealTypes),
            onFoodUpdated = onFoodUpdated,
        )

    private fun makeDate(hour: Int, minute: Int): Instant =
        ZonedDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant()

    private fun makeFood(
        foodItemId: String = "12345",
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
        mealTypeId: String? = null,
        date: Instant = Instant.now(),
    ): FoodConsumedDomain = FoodConsumedDomain(
        id = "1",
        foodItemId = foodItemId,
        foodItemKind = kind,
        czName = "Ovesné vločky",
        engName = "Oats",
        weight = 80.0,
        date = date,
        calories = 295,
        caloriesPerHundredGrams = 368.75,
        energyKJ = 1544.0,
        protein = 10.0,
        carbohydrate = 52.0,
        carbohydrateSugar = 8.0,
        fat = 5.0,
        fatSaturated = 1.0,
        fatUnsaturated = 2.0,
        fiber = 6.0,
        salt = 0.1,
        mealTypeId = mealTypeId,
    )
}
