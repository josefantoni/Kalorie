package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodConsumedDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CopyFoodsConsumedUseCaseTest {

    // MARK: - Tests

    @Test
    fun copy_whenNotAuthenticated_throwsAuthErrorAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut(listOf(makeFood(id = "a")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
        assertNull(dataProvider.batchSavedCollection)
    }

    @Test
    fun copy_writesEveryEntryInOneBatchIntoTheUserFoodConsumedCollection() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut(listOf(makeFood(id = "a"), makeFood(id = "b")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        assertEquals("users/user-123/foodConsumed", dataProvider.batchSavedCollection)
        assertEquals(2, dataProvider.batchSavedCount)
    }

    @Test
    fun copy_givesEveryEntryANewIdMatchingItsDocumentId() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeFood(id = "a"), makeFood(id = "b")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        val ids = writtenDTOs(dataProvider).map { it.id }
        assertEquals(2, ids.toSet().size)
        assertFalse(ids.contains("a") || ids.contains("b"))
        assertEquals(ids, dataProvider.batchSavedItems.map { it.second })
        assertTrue(ids.all { it == it.uppercase() })
    }

    @Test
    fun copy_alwaysPinsTheTargetMealTypeEvenWhenTheSourceWasPinnedElsewhere() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeFood(id = "a", mealTypeId = "breakfast")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        assertEquals("lunch", writtenDTOs(dataProvider).first().mealTypeId)
    }

    @Test
    fun copy_carriesMeasureUnitKindAndNutritionUnchanged() = runTest {
        val (sut, dataProvider) = makeSUT()
        val source = makeFood(id = "a", kind = FoodItemKind.CREATED_MEAL, measure = FoodMeasure.MILLILITRES)

        sut(listOf(source), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        val copy = writtenDTOs(dataProvider).first()
        assertEquals(FoodMeasure.MILLILITRES.rawValue, copy.measureUnit)
        assertEquals(FoodItemKind.CREATED_MEAL, copy.foodItemKind)
        assertEquals(source.foodItemId, copy.foodItemId)
        assertEquals(source.weight, copy.weight, 0.0)
        assertEquals(source.calories, copy.calories)
        assertEquals(source.protein, copy.protein, 0.0)
        assertEquals(source.caloriesPerHundredGrams, copy.caloriesPerHundredGrams)
    }

    @Test
    fun copy_toAnotherDay_usesTheTargetWindowStartOnThatDay() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeFood(id = "a")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        assertEquals(epochSeconds(pastDay, hour = 11), writtenDTOs(dataProvider).first().date, 0.0)
    }

    @Test
    fun copy_keepsSourceOrderByOffsettingEachEntryOneSecond() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut(listOf(makeFood(id = "a"), makeFood(id = "b"), makeFood(id = "c")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        val dates = writtenDTOs(dataProvider).map { it.date }
        assertEquals(1.0, dates[1] - dates[0], 0.0)
        assertEquals(1.0, dates[2] - dates[1], 0.0)
    }

    @Test
    fun copy_toTodayWhileNowIsInsideTheTargetWindow_usesNow() = runTest {
        val now = instantToday(hour = 12, minute = 30, second = 40)
        val (sut, dataProvider) = makeSUT(now = now)

        sut(listOf(makeFood(id = "a")), toDay = now, mealTypeId = "lunch", mealTypes = mealTypes)

        assertEquals(epochSeconds(now, hour = 12, minute = 30), writtenDTOs(dataProvider).first().date, 0.0)
    }

    @Test
    fun copy_toTodayWhileNowIsOutsideTheTargetWindow_usesWindowStart() = runTest {
        val now = instantToday(hour = 8, minute = 15)
        val (sut, dataProvider) = makeSUT(now = now)

        sut(listOf(makeFood(id = "a")), toDay = now, mealTypeId = "lunch", mealTypes = mealTypes)

        assertEquals(epochSeconds(now, hour = 11), writtenDTOs(dataProvider).first().date, 0.0)
    }

    @Test
    fun copy_toAPastDayWhileNowWouldFitTheWindow_stillUsesWindowStart() = runTest {
        val (sut, dataProvider) = makeSUT(now = instantToday(hour = 12, minute = 30))

        sut(listOf(makeFood(id = "a")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)

        assertEquals(epochSeconds(pastDay, hour = 11), writtenDTOs(dataProvider).first().date, 0.0)
    }

    @Test
    fun copy_whenTargetMealTypeNoLongerExists_throwsAndWritesNothing() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut(listOf(makeFood(id = "a")), toDay = pastDay, mealTypeId = "deleted", mealTypes = mealTypes)
            fail("Expected mealTypeNotFound error")
        } catch (_: CopyFoodsConsumedError.MealTypeNotFound) {
        }
        assertNull(dataProvider.batchSavedCollection)
    }

    @Test
    fun copy_whenBatchFails_propagatesTheError() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedBatchError = IOException("offline")

        try {
            sut(listOf(makeFood(id = "a")), toDay = pastDay, mealTypeId = "lunch", mealTypes = mealTypes)
            fail("Expected the batch error")
        } catch (error: IOException) {
            assertEquals("offline", error.message)
        }
    }

    // MARK: - Helpers

    private val zone: ZoneId = ZoneId.systemDefault()

    private val pastDay: Instant
        get() = Instant.now().atZone(zone).minusDays(3).toInstant()

    private val mealTypes = listOf(
        MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 7 * 60, endMinutes = 10 * 60),
        MealTypeDomain(id = "lunch", name = "Lunch", startMinutes = 11 * 60, endMinutes = 14 * 60),
    )

    private fun instantToday(hour: Int, minute: Int = 0, second: Int = 0): Instant = LocalDate.now(zone).atTime(hour, minute, second).atZone(zone).toInstant()

    private fun epochSeconds(day: Instant, hour: Int, minute: Int = 0): Double = day.atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toEpochSecond().toDouble()

    private fun writtenDTOs(dataProvider: FirestoreDataProviderFake): List<FoodConsumedDTO> = dataProvider.batchSavedItems.mapNotNull { it.first as? FoodConsumedDTO }

    private fun makeSUT(userId: String? = "test-user", now: Instant = Instant.now()): Pair<CopyFoodsConsumedUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = CopyFoodsConsumedUseCase(
            dataProvider = dataProvider,
            authProvider = AuthProviderFake(userId = userId),
            now = { now },
        )
        return sut to dataProvider
    }

    private fun makeFood(
        id: String,
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
        measure: FoodMeasure = FoodMeasure.GRAMS,
        mealTypeId: String? = null,
    ) = FoodConsumedDomain(
        id = id,
        foodItemId = "item-$id",
        foodItemKind = kind,
        czName = "Jídlo",
        engName = "Food",
        weight = 150.0,
        date = Instant.now(),
        calories = 300,
        caloriesPerHundredGrams = 200.0,
        energyKJ = 1255.0,
        protein = 12.0,
        carbohydrate = 30.0,
        carbohydrateSugar = 6.0,
        fat = 8.0,
        fatSaturated = 2.0,
        fatUnsaturated = 4.0,
        fiber = 3.0,
        salt = 0.4,
        mealTypeId = mealTypeId,
        measure = measure,
    )
}
