package antoni.kalorie.features.dashboard

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseFake
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseFake
import antoni.kalorie.core.usecases.CopyFoodsConsumedUseCaseFake
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.FetchAppSettingsUseCaseFake
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseFake
import antoni.kalorie.core.usecases.FetchDailyGoalsUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodsConsumedInRangeUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseFake
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseFake
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DashboardViewModelReviewPromptTest {

    @Test
    fun onFoodAdded_whenTheThirdLoggingDayBegins_showsTheCardRightAway() = runTest {
        val clock = Clock(day(10))
        val sut = makeSUT(store = ReviewPromptStoreFake(), clock = clock)
        sut.onAppear()

        sut.onFoodAdded()
        clock.advance(DAY)
        sut.onFoodAdded()
        assertFalse("the ask comes only after real use, not on the second day", sut.isReviewCardVisible.value)

        clock.advance(DAY)
        sut.onFoodAdded()
        assertTrue("on the third day the card must appear right after the first save, not on the next launch", sut.isReviewCardVisible.value)
    }

    @Test
    fun onFoodAdded_whenSeveralFoodsAreAddedOnOneDay_countsOneLoggingDay() = runTest {
        val store = ReviewPromptStoreFake()
        val sut = makeSUT(store = store, clock = Clock(day(10)))
        sut.onAppear()

        sut.onFoodAdded()
        sut.onFoodAdded()
        sut.onFoodAdded()

        assertEquals("the rule measures days of use, not entries", 1, store.loggingDays.size)
        assertFalse(sut.isReviewCardVisible.value)
    }

    @Test
    fun onFoodAdded_whenAPastDayIsSelected_recordsTodayNotTheSelectedDay() = runTest {
        val clock = Clock(day(10))
        val store = ReviewPromptStoreFake()
        val sut = makeSUT(store = store, clock = clock)
        sut.onAppear()

        sut.onDaySelected(Instant.now().minus(Duration.ofDays(5)))
        sut.onFoodAdded()

        assertEquals("backfilling is not three days of use", setOf(BodyWeightDayKey.make(clock.now)), store.loggingDays)
    }

    @Test
    fun onFoodConsumedUpdated_doesNotRecordALoggingDay() = runTest {
        val store = ReviewPromptStoreFake()
        val sut = makeSUT(store = store, clock = Clock(day(10)))
        sut.onAppear()

        sut.onFoodConsumedUpdated()

        assertTrue("editing an existing entry is not logging a day", store.loggingDays.isEmpty())
    }

    @Test
    fun onReviewNotNowTapped_bringsTheCardBackAtThreeFiveEightThirteenThenEveryTwentyOneDays() = runTest {
        val clock = Clock(day(10))
        val sut = makeSUT(store = dueStore(), clock = clock)
        sut.onAppear()
        assertTrue(sut.isReviewCardVisible.value)

        for (days in listOf(3L, 5L, 8L, 13L, 21L, 21L, 21L)) {
            sut.onReviewNotNowTapped()
            assertFalse(sut.isReviewCardVisible.value)

            clock.advance(Duration.ofDays(days).minusSeconds(1))
            sut.onRefresh()
            assertFalse("one second before $days days the card must still be snoozed", sut.isReviewCardVisible.value)

            clock.advance(Duration.ofSeconds(1))
            sut.onRefresh()
            assertTrue("after $days days the card must return", sut.isReviewCardVisible.value)
        }
    }

    @Test
    fun onReviewNotNowTapped_whenTappedTwice_countsOneDismissal() = runTest {
        val store = dueStore()
        val sut = makeSUT(store = store, clock = Clock(day(10)))
        sut.onAppear()

        sut.onReviewNotNowTapped()
        sut.onReviewNotNowTapped()

        assertEquals("a double tap must not skip a step of the 3/5/8/13 schedule", 1, store.dismissCount)
    }

    @Test
    fun onReviewRateTapped_hidesTheCardForGood() = runTest {
        val clock = Clock(day(10))
        val store = dueStore()
        val sut = makeSUT(store = store, clock = clock)
        sut.onAppear()

        sut.onReviewRateTapped()
        assertFalse(sut.isReviewCardVisible.value)
        assertTrue(store.isCompleted)

        store.dismissCount = 1
        store.lastDismissedAt = clock.now.minus(Duration.ofDays(30))
        sut.onRefresh()
        assertFalse("a user who rated is never asked again", sut.isReviewCardVisible.value)
    }

    @Test
    fun onAppear_whenTheSpotlightWasShownToday_hidesTheCardUntilTomorrow() = runTest {
        val clock = Clock(day(10))
        val spotlightStore = SignInSpotlightStoreFake(lastShownAt = clock.now.minusSeconds(60))
        val sut = makeSUT(store = dueStore(), spotlightStore = spotlightStore, clock = clock)
        sut.onAppear()
        assertFalse("one ask per day, and the diary-safety ask wins", sut.isReviewCardVisible.value)

        clock.advance(DAY)
        sut.onRefresh()
        assertTrue(sut.isReviewCardVisible.value)
    }

    @Test
    fun onAppear_whenTheSelectedDayIsEmpty_doesNotShowTheCard() = runTest {
        val sut = makeSUT(store = dueStore(), foods = emptyList(), clock = Clock(day(10)))
        sut.onAppear()
        assertFalse("the card sits under the day summary, and an empty day shows the empty state", sut.isReviewCardVisible.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onAppear_whenTheSpotlightFiresInTheSamePass_neverShowsTheCard() = runTest {
        val clock = Clock(day(10))
        val spotlightStore = SignInSpotlightStoreFake(lastShownAt = clock.now.minus(Duration.ofDays(8)))
        val sut = makeSUT(store = dueStore(), spotlightStore = spotlightStore, clock = clock, isAnonymous = true)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.isReviewCardVisible.collect { values += it } }

        sut.onAppear()

        assertTrue(sut.isSignInSpotlightVisible.value)
        assertFalse("the spotlight wins the pass, so the card must never flash first: $values", values.contains(true))
    }

    @Test
    fun onFoodAdded_whenTheAddFoodSheetIsStillUp_doesNotShowTheCardBehindIt() = runTest {
        val clock = Clock(day(10))
        val spotlightStore = SignInSpotlightStoreFake(lastShownAt = clock.now.minus(Duration.ofDays(8)))
        val sut = makeSUT(store = dueStore(), spotlightStore = spotlightStore, clock = clock, isAnonymous = true)
        sut.showAddFoodSheet.value = true
        sut.onAppear()

        sut.onFoodAdded()
        assertFalse(sut.isReviewCardVisible.value)

        sut.onAddFoodSheetDismissed()
        assertTrue(sut.isSignInSpotlightVisible.value)
        assertFalse("the spotlight that fires after the sheet wins the day", sut.isReviewCardVisible.value)
    }

    // MARK: - Helpers

    private class Clock(var now: Instant) {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }

    private fun day(dayOfMonth: Int): Instant = LocalDate.of(2026, 11, dayOfMonth).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()

    private fun dueStore(dismissCount: Int = 0, lastDismissedAt: Instant? = null) = ReviewPromptStoreFake(
        loggingDays = setOf("2026-11-01", "2026-11-02", "2026-11-03"),
        dismissCount = dismissCount,
        lastDismissedAt = lastDismissedAt,
    )

    private fun makeSUT(
        store: ReviewPromptStoreFake,
        spotlightStore: SignInSpotlightStoreFake = SignInSpotlightStoreFake(),
        foods: List<FoodConsumedDomain> = listOf(makeFood()),
        clock: Clock,
        isAnonymous: Boolean = false,
    ): DashboardViewModel = DashboardViewModel(
        fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = listOf(MealTypeDomain(id = "0", name = "Meal", startMinutes = 0, endMinutes = 23 * 60))),
        fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods = foods),
        fetchFoodsConsumedInRange = FetchFoodsConsumedInRangeUseCaseFake(stubbedFoods = foods),
        setupDefaultMeals = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty = ConfirmMealTypesEmptyUseCaseFake(stubbedResult = true),
        deleteFoodConsumed = DeleteFoodConsumedUseCaseFake(),
        copyFoodsConsumed = CopyFoodsConsumedUseCaseFake(),
        assignFoodMealType = AssignFoodMealTypeUseCaseFake(),
        fetchBodyWeights = FetchBodyWeightsUseCaseFake(),
        fetchAppSettings = FetchAppSettingsUseCaseFake(),
        fetchDailyGoals = FetchDailyGoalsUseCaseFake(),
        saveBodyWeight = SaveBodyWeightUseCaseFake(),
        authProvider = AuthProviderFake(isAnonymous = isAnonymous),
        signInSpotlightStore = spotlightStore,
        reviewPromptStore = store,
        now = { clock.now },
    )

    private fun makeFood() = FoodConsumedDomain(
        id = "f1",
        foodItemId = "f1",
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = "Jídlo",
        skName = "Jídlo",
        engName = "Food",
        weight = 100.0,
        date = Instant.now(),
        calories = 200,
        caloriesPerHundredGrams = 200.0,
        energyKJ = 837.0,
        protein = 10.0,
        carbohydrate = 20.0,
        carbohydrateSugar = 5.0,
        fat = 5.0,
        fatSaturated = 1.0,
        fatUnsaturated = 2.0,
        fiber = 1.0,
        salt = 0.2,
        mealTypeId = null,
    )

    private companion object {
        val DAY: Duration = Duration.ofDays(1)
    }
}
