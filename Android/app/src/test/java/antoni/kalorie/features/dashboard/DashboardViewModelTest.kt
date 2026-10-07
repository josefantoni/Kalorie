package antoni.kalorie.features.dashboard

import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderError
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseFake
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseProtocol
import antoni.kalorie.core.usecases.CopyFoodsConsumedUseCaseFake
import antoni.kalorie.core.usecases.CopyFoodsConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedInRangeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodsConsumedInRangeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseFake
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.isSameDay
import antoni.kalorie.core.utils.minutesSinceMidnight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class DashboardViewModelTest {

    @Test
    fun dailyMacros_whenAFoodsFiberIsUnknown_showsZeroInsteadOfExcludingIt() {
        val macros = DailyMacros(listOf(makeFood(id = "1", hour = 8, fiber = null), makeFood(id = "2", hour = 9, fiber = 3.0)))

        assertEquals(3.0, macros.fiber, 0.0)
    }

    @Test
    fun groupedFoods_withNoFoodsConsumed_returnsEmpty() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = emptyList()

        assertTrue(sut.groupedFoods.isEmpty())
    }

    @Test
    fun groupedFoods_foodWithinRange_isAssignedToMealType() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 10))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("0", groups[0].mealType?.id)
        assertEquals("f1", groups[0].foods.first().id)
    }

    @Test
    fun groupedFoods_foodAtExactStartTime_isIncluded() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 8, minute = 0))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("0", groups[0].mealType?.id)
    }

    @Test
    fun groupedFoods_foodAtExactEndTime_isExcluded() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 12, minute = 0))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertNull(groups[0].mealType)
        assertEquals("f1", groups[0].foods.first().id)
    }

    @Test
    fun groupedFoods_foodOutsideAllRanges_goesToNilGroup() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 7))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertNull(groups[0].mealType)
    }

    @Test
    fun groupedFoods_nilGroupAppearsLast() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "assigned", hour = 10), makeFood(id = "unassigned", hour = 7))

        val groups = sut.groupedFoods

        assertEquals(2, groups.size)
        assertNotNull(groups[0].mealType)
        assertNull(groups[1].mealType)
    }

    @Test
    fun groupedFoods_sortsMealTypeGroupsByStartTime() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 1, hour = 12, endHour = 16), makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "early", hour = 9), makeFood(id = "late", hour = 13))

        val groups = sut.groupedFoods

        assertEquals(2, groups.size)
        assertEquals("0", groups[0].mealType?.id)
        assertEquals("1", groups[1].mealType?.id)
    }

    @Test
    fun groupedFoods_foodInOverlappingWindows_isAssignedToEarlierWindowOnly() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 14), makeMealType(id = 1, hour = 12, endHour = 16))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 13))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("0", groups[0].mealType?.id)
        assertEquals(listOf("f1"), groups[0].foods.map { it.id })
    }

    @Test
    fun groupedFoods_wrappingMealType_includesFoodAfterMidnight() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 23, endHour = 1))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 0, minute = 30))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("0", groups[0].mealType?.id)
    }

    @Test
    fun groupedFoods_pinnedFood_isAssignedToPinnedMealTypeRegardlessOfTime_andKeepsItsLoggedDate() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(
            makeMealType(id = 0, hour = 7, endHour = 10),
            makeMealType(id = 1, hour = 18, endHour = 21),
        )
        val loggedAt22 = makeFood(id = "f1", hour = 22, mealTypeId = "0")
        sut.foodsConsumed.value = listOf(loggedAt22)

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("a pin must move the entry into its meal section even though 22:00 falls in neither window", "0", groups[0].mealType?.id)
        assertEquals("the pin must change the section only — the logged timestamp stays untouched", loggedAt22.date, groups[0].foods.first().date)
    }

    @Test
    fun groupedFoods_pinnedFood_overridesAWindowItsOwnTimeWouldOtherwiseFallInto() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(
            makeMealType(id = 0, hour = 8, endHour = 12),
            makeMealType(id = 1, hour = 12, endHour = 16),
        )
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 9, mealTypeId = "1"))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("the pin must win even when the entry's own time falls inside a different window", "1", groups[0].mealType?.id)
    }

    @Test
    fun groupedFoods_unknownPinnedMealTypeId_fallsBackToWindowAssignment() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 9, mealTypeId = "deleted-meal-type"))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("a pin naming a meal type that no longer exists must be treated as no pin, not as a dead-end", "0", groups[0].mealType?.id)
    }

    @Test
    fun groupedFoods_unknownPinnedMealTypeId_fallsBackToUnassignedWhenNoWindowMatches() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 22, mealTypeId = "deleted-meal-type"))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertNull("an unresolvable pin with no matching window must land in the unassigned section, not vanish", groups[0].mealType)
        assertEquals(listOf("f1"), groups[0].foods.map { it.id })
    }

    @Test
    fun groupedFoods_mealTypeWithNoMatchingFoods_isOmitted() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(
            makeMealType(id = 0, hour = 8, endHour = 12),
            makeMealType(id = 1, hour = 12, endHour = 16),
        )
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 9))

        val groups = sut.groupedFoods

        assertEquals(1, groups.size)
        assertEquals("0", groups[0].mealType?.id)
    }

    @Test
    fun onAppear_whenMealTypesEmpty_callsSetupDefaultMeals() = runTest {
        val sut = makeSUT(
            fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = emptyList()),
            setupDefaultMeals = SetupDefaultMealsUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 8, endHour = 12))),
        )

        sut.onAppear()

        assertFalse(sut.mealTypes.value.isEmpty())
    }

    @Test
    fun onAppear_whenFetchSucceeds_setsLoadedStateAndNoAlert() = runTest {
        val sut = makeSUT(fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 8, endHour = 12))))

        sut.onAppear()

        assertFalse(sut.state.value.isLoading)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_calledAgainAfterInitialLoad_doesNotResetSelectedDayOrFoods() = runTest {
        val sut = makeSUT(fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 8, endHour = 12))))
        sut.onAppear()
        val yesterday = ZonedDateTime.now(ZoneId.systemDefault()).minusDays(1).toInstant()
        sut.onDaySelected(yesterday)
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 10))

        sut.onAppear()

        assertTrue(
            "a recomposed LaunchedEffect re-runs onAppear when a pushed detail view is popped back to the Dashboard; a second onAppear must not silently jump the user back to today",
            sut.selectedDay.value.isSameDay(yesterday),
        )
        assertEquals(listOf("f1"), sut.foodsConsumed.value.map { it.id })
    }

    @Test
    fun onAppear_whenFetchFails_showsAlert() = runTest {
        val sut = makeSUT(fetchMealTypes = FetchMealTypesUseCaseFake(shouldThrow = true))

        sut.onAppear()

        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenFetchFailsOffline_showsOfflineAlert() = runTest {
        val sut = makeSUT(
            fetchMealTypes = FetchMealTypesUseCaseFake(shouldThrow = true, errorToThrow = FirestoreDataProviderError.Unreachable),
        )

        sut.onAppear()

        assertEquals("a Firestore unavailable error must be distinguishable from any other failure", R.string.common_error_offline, sut.alertItem.value?.titleRes)
        assertEquals("the offline alert must tell the user what to do about it, not only what happened", R.string.common_error_offline_message, sut.alertItem.value?.messageRes)
    }

    @Test
    fun onAppear_whenFetchFailsWithOtherError_showsUnknownErrorAlert() = runTest {
        val sut = makeSUT(fetchMealTypes = FetchMealTypesUseCaseFake(shouldThrow = true, errorToThrow = RuntimeException("unknown")))

        sut.onAppear()

        assertEquals("a non-offline error must not be mistaken for offline", R.string.common_error_unknown, sut.alertItem.value?.titleRes)
        assertEquals("the unknown-error alert must carry a body line too, so AlertItem.messageRes has a producer", R.string.common_error_unknown_message, sut.alertItem.value?.messageRes)
    }

    @Test
    fun onAppear_whenMealTypesEmptyButNotConfirmedByServer_doesNotCallSetupDefaultMeals() = runTest {
        val sut = makeSUT(
            fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = emptyList()),
            setupDefaultMeals = SetupDefaultMealsUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 8, endHour = 12))),
            confirmMealTypesEmpty = ConfirmMealTypesEmptyUseCaseFake(stubbedError = RuntimeException("not connected to the internet")),
        )

        sut.onAppear()

        assertTrue(sut.mealTypes.value.isEmpty())
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onRefresh_beforeInitialLoadCompletes_doesNothing() = runTest {
        val sut = makeSUT(fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 8, endHour = 12))))

        sut.onRefresh()

        assertTrue("a day-change broadcast racing the cold-launch load must not run its own fetch on top of onAppear's", sut.mealTypes.value.isEmpty())
    }

    @Test
    fun onRefresh_afterInitialLoadCompletes_refetches() = runTest {
        val sut = makeSUT(fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 8, endHour = 12))))
        sut.onAppear()

        sut.onRefresh()

        assertFalse(sut.mealTypes.value.isEmpty())
    }

    @Test
    fun onRefresh_afterInitialLoadCompletes_reloadsTheWholeMonth() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy()
        val range = FetchFoodsConsumedInRangeUseCaseSpy()
        val sut = makeSUT(fetchMealTypes = makeSingleMealType(), fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()

        sut.onRefresh()

        assertEquals("pull-to-refresh is the user's explicit reload-everything action", 2, month.months.size)
        assertTrue(range.ranges.isEmpty())
    }

    @Test
    fun onForeground_beforeInitialLoadCompletes_doesNothing() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy()
        val range = FetchFoodsConsumedInRangeUseCaseSpy()
        val sut = makeSUT(fetchMealTypes = makeSingleMealType(), fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)

        sut.onForeground()

        assertTrue("a resume racing the cold-launch load must not run its own fetch on top of onAppear's", sut.mealTypes.value.isEmpty())
        assertTrue(month.months.isEmpty())
        assertTrue(range.ranges.isEmpty())
    }

    @Test
    fun onForeground_afterInitialLoad_reloadsOnlyTheSelectedDay() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "f1", hour = 8))))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(makeFood(id = "f2", hour = 9)))
        val sut = makeSUT(fetchMealTypes = makeSingleMealType(), fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()

        sut.onForeground()

        assertEquals("a change made on another device must show up on resume", listOf("f2"), sut.foodsConsumed.value.map { it.id })
        assertSingleDayReload(range, month, sut.selectedDay.value)
    }

    @Test
    fun onDeleteRequested_showsConfirmation() {
        val sut = makeSUT()
        assertFalse(sut.isDeleteConfirmationVisible.value)

        sut.onDeleteRequested(makeFood(id = "f1", hour = 8))

        assertTrue(sut.isDeleteConfirmationVisible.value)
    }

    @Test
    fun onDeleteConfirmed_withoutPendingRequest_doesNothing() = runTest {
        val sut = makeSUT()
        sut.foodsConsumed.value = listOf(makeFood(id = "f1", hour = 8))

        sut.onDeleteConfirmed()

        assertEquals(listOf("f1"), sut.foodsConsumed.value.map { it.id })
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onDeleteConfirmed_whenDeleteSucceeds_reloadsOnlyTheSelectedDay() = runTest {
        val remaining = makeFood(id = "f2", hour = 9)
        val toDelete = makeFood(id = "f1", hour = 8)
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(toDelete, remaining)))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(remaining))
        val sut = makeSUT(fetchMealTypes = makeSingleMealType(), fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()

        sut.onDeleteRequested(toDelete)
        sut.onDeleteConfirmed()

        assertEquals("a confirmed delete must refetch the day so the removed entry disappears", listOf("f2"), sut.foodsConsumed.value.map { it.id })
        assertNull(sut.alertItem.value)
        assertSingleDayReload(range, month, sut.selectedDay.value)
    }

    @Test
    fun onDeleteConfirmed_whenDeleteFails_showsAlertAndKeepsExistingFoods() = runTest {
        val existing = makeFood(id = "f1", hour = 8)
        val sut = makeSUT(deleteFoodConsumed = DeleteFoodConsumedUseCaseFake(shouldThrow = true))
        sut.foodsConsumed.value = listOf(existing)

        sut.onDeleteRequested(existing)
        sut.onDeleteConfirmed()

        assertEquals("a failed delete must not silently drop the entry from the list", listOf("f1"), sut.foodsConsumed.value.map { it.id })
        assertEquals(R.string.dashboard_error_deleteFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onDayChanged_toAnotherDayOfAnAlreadyLoadedMonth_doesNotFetchAgain() = runTest {
        val monthFetches = FetchFoodsConsumedForMonthUseCaseSpy()
        val sut = makeSUT(fetchFoodsConsumedForMonth = monthFetches)
        sut.onAppear()

        sut.onDayChanged(firstOfCurrentMonth())

        assertEquals(1, monthFetches.months.size)
    }

    @Test
    fun onDayChanged_toAnUnloadedMonth_fetchesItOnceAndThenServesItFromTheCache() = runTest {
        val monthFetches = FetchFoodsConsumedForMonthUseCaseSpy()
        val sut = makeSUT(fetchFoodsConsumedForMonth = monthFetches)
        sut.onAppear()
        val lastMonth = firstOfCurrentMonth().atZone(ZoneId.systemDefault()).minusMonths(1).toInstant()

        sut.onDayChanged(lastMonth)
        sut.onDayChanged(lastMonth)

        assertEquals(2, monthFetches.months.size)
    }

    @Test
    fun onFoodConsumedUpdated_reloadsOnlyTheSelectedDay() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "before", hour = 9))))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(makeFood(id = "after", hour = 9)))
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()

        sut.onFoodConsumedUpdated()

        assertEquals(listOf("after"), sut.foodsConsumed.value.map { it.id })
        assertSingleDayReload(range, month, sut.selectedDay.value)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun onFoodConsumedUpdated_whenAnotherMonthIsLoadedWhileTheDayFetchIsInFlight_keepsTheReloadedDayInTheCache() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "before", hour = 9)), emptyList()))
        val range = FetchFoodsConsumedInRangeUseCaseSuspending()
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        val today = sut.selectedDay.value
        val reload = launch { sut.onFoodConsumedUpdated() }
        runCurrent()

        sut.onDayChanged(firstOfCurrentMonth().atZone(ZoneId.systemDefault()).minusMonths(1).toInstant())
        range.gate.complete(listOf(makeFood(id = "after", hour = 9)))
        reload.join()
        sut.onDayChanged(today)

        assertEquals("a reload finishing after the cache was rebuilt must not be lost", listOf("after"), sut.foodsConsumed.value.map { it.id })
    }

    @Test
    fun onFoodConsumedUpdated_replacesTheDayInsteadOfMergingIntoIt() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "f1", hour = 8), makeFood(id = "f2", hour = 9))))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(makeFood(id = "f1", hour = 8)))
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        assertEquals(2, sut.foodsConsumed.value.size)

        sut.onFoodConsumedUpdated()

        assertEquals("an entry deleted on another device must disappear", listOf("f1"), sut.foodsConsumed.value.map { it.id })
    }

    @Test
    fun onFoodConsumedUpdated_whenTheDayBecomesEmpty_removesItFromTheActiveDays() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "f1", hour = 8))))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = emptyList())
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        val day = sut.selectedDay.value.atZone(ZoneId.systemDefault()).dayOfMonth
        assertTrue(day in sut.activeDaysInMonth.value)

        sut.onFoodConsumedUpdated()

        assertFalse("a calendar dot for an empty day would point at nothing", day in sut.activeDaysInMonth.value)
        assertTrue(sut.foodsConsumed.value.isEmpty())
    }

    @Test
    fun onForeground_whileTheCalendarShowsAnotherMonth_keepsThatMonthsActiveDays() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "f1", hour = 8)), emptyList()))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(makeFood(id = "f1", hour = 8)))
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        val today = sut.selectedDay.value.atZone(ZoneId.systemDefault()).dayOfMonth
        sut.onCalendarMonthChanged(firstOfCurrentMonth().atZone(ZoneId.systemDefault()).minusMonths(1).toInstant())

        sut.onForeground()

        assertTrue("the open calendar must not show the selected month's dots on another month", sut.activeDaysInMonth.value.isEmpty())

        sut.onCalendarDismissed()

        assertEquals("the day picker shows the selected day's month once the calendar closes", setOf(today), sut.activeDaysInMonth.value)
    }

    @Test
    fun onFoodConsumedUpdated_whenTheMonthIsNotCached_loadsTheWholeMonth() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy()
        val range = FetchFoodsConsumedInRangeUseCaseSpy()
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        sut.selectedDay.value = ZonedDateTime.now().minusMonths(1).toInstant()

        sut.onFoodConsumedUpdated()

        assertEquals("there is no month cache to splice a single day into", 2, month.months.size)
        assertTrue(range.ranges.isEmpty())
    }

    @Test
    fun onFoodConsumedUpdated_whenTheMonthIsNotCached_showsTheActiveDaysOfThatMonth() = runTest {
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(makeFood(id = "f1", hour = 8)), emptyList()))
        val sut = makeSUT(fetchFoodsConsumedForMonth = month)
        sut.onAppear()
        assertTrue(sut.activeDaysInMonth.value.isNotEmpty())
        sut.selectedDay.value = ZonedDateTime.now().minusMonths(1).toInstant()

        sut.onFoodConsumedUpdated()

        assertTrue(
            "the day picker must not keep the previous month's dots under the new month's day numbers",
            sut.activeDaysInMonth.value.isEmpty(),
        )
    }

    @Test
    fun onCalendarMonthChanged_forALoadedMonth_doesNotFetchButForAnUnloadedOneDoes() = runTest {
        val monthFetches = FetchFoodsConsumedForMonthUseCaseSpy()
        val sut = makeSUT(fetchFoodsConsumedForMonth = monthFetches)
        sut.onAppear()

        sut.onCalendarMonthChanged(firstOfCurrentMonth())
        assertEquals(1, monthFetches.months.size)

        sut.onCalendarMonthChanged(firstOfCurrentMonth().atZone(ZoneId.systemDefault()).minusMonths(1).toInstant())
        assertEquals(2, monthFetches.months.size)
    }

    @Test
    fun onDaySelected_closesTheCalendarSheetAndSelectsTheDay() = runTest {
        val sut = makeSUT()
        sut.onAppear()
        sut.showCalendarSheet.value = true
        val day = firstOfCurrentMonth()

        sut.onDaySelected(day)

        assertFalse(sut.showCalendarSheet.value)
        assertTrue(sut.selectedDay.value.isSameDay(day))
    }

    @Test
    fun onCopyRequested_opensTheBoxOnTodayAndTheWindowTheCurrentTimeFallsIn() {
        val sut = makeSUT()
        val wholeDay = makeMealType(id = 0, hour = 0, endHour = 24)
        val source = makeMealType(id = 1, hour = 1, endHour = 2)
        sut.mealTypes.value = listOf(source, wholeDay)

        sut.onCopyRequested(source, index = 2)

        assertEquals(2, sut.copyPopoverIndex.value)
        assertTrue(sut.copyTargetDay.value.isSameDay(Instant.now()))
        assertEquals("0", sut.copyTargetMealTypeId.value)
    }

    @Test
    fun onCopyRequested_whenNoWindowContainsNow_defaultsToTheSourceMealType() {
        val sut = makeSUT()
        val source = makeMealTypeExcludingNow(id = 1)
        sut.mealTypes.value = listOf(makeMealTypeExcludingNow(id = 0, offsetMinutes = 300), source)

        sut.onCopyRequested(source, index = 0)

        assertEquals("1", sut.copyTargetMealTypeId.value)
    }

    @Test
    fun onCopyRequested_whenNoWindowContainsNowAndSourceIsUnassigned_defaultsToTheFirstMealType() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealTypeExcludingNow(id = 0), makeMealTypeExcludingNow(id = 1, offsetMinutes = 300))

        sut.onCopyRequested(null, index = 0)

        assertEquals("0", sut.copyTargetMealTypeId.value)
    }

    @Test
    fun canCopy_whenTargetIsTheSameDayAndSameMealType_isFalse() {
        val sut = makeSUT()
        val source = makeMealType(id = 0, hour = 8, endHour = 12)
        sut.mealTypes.value = listOf(source)
        sut.copyTargetDay.value = sut.selectedDay.value
        sut.copyTargetMealTypeId.value = "0"

        assertFalse(sut.canCopy(source))
    }

    @Test
    fun canCopy_whenOnlyTheMealTypeDiffers_isTrue() {
        val sut = makeSUT()
        val source = makeMealType(id = 0, hour = 8, endHour = 12)
        sut.copyTargetDay.value = sut.selectedDay.value
        sut.copyTargetMealTypeId.value = "1"

        assertTrue(sut.canCopy(source))
    }

    @Test
    fun canCopy_whenOnlyTheDayDiffers_isTrue() {
        val sut = makeSUT()
        val source = makeMealType(id = 0, hour = 8, endHour = 12)
        sut.copyTargetDay.value = sut.selectedDay.value.minus(1, ChronoUnit.DAYS)
        sut.copyTargetMealTypeId.value = "0"

        assertTrue(sut.canCopy(source))
    }

    @Test
    fun canCopy_whenSourceIsUnassigned_anyTargetCountsAsDifferent() {
        val sut = makeSUT()
        sut.copyTargetDay.value = sut.selectedDay.value
        sut.copyTargetMealTypeId.value = "0"

        assertTrue(sut.canCopy(null))
    }

    @Test
    fun canCopy_withoutATargetMealType_isFalse() {
        val sut = makeSUT()
        sut.copyTargetMealTypeId.value = null

        assertFalse(sut.canCopy(null))
    }

    @Test
    fun onCopyConfirmed_whenCopySucceeds_reloadsTheDayAndClosesTheBox() = runTest {
        val source = makeFood(id = "f1", hour = 8)
        val copied = makeFood(id = "f2", hour = 12)
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(source)))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(source, copied))
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        val sourceType = makeMealType(id = 0, hour = 8, endHour = 10)
        sut.mealTypes.value = listOf(sourceType, makeMealType(id = 1, hour = 11, endHour = 14))
        sut.copyTargetDay.value = sut.selectedDay.value
        sut.copyTargetMealTypeId.value = "1"
        sut.copyPopoverIndex.value = 0

        sut.onCopyConfirmed(listOf(source), sourceType)

        assertEquals(listOf("f1", "f2"), sut.foodsConsumed.value.map { it.id })
        assertNull(sut.copyPopoverIndex.value)
        assertFalse(sut.showCopyCheckmark.value)
        assertFalse(sut.isCopying.value)
        assertNull(sut.alertItem.value)
        assertSingleDayReload(range, month, sut.selectedDay.value)
    }

    @Test
    fun onCopyConfirmed_whenTargetIsAnotherDayOfTheCachedMonth_reloadsOnlyThatDay() = runTest {
        val source = makeFood(id = "f1", hour = 8)
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(source)))
        val range = FetchFoodsConsumedInRangeUseCaseSpy(result = listOf(makeFood(id = "f2", hour = 12)))
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        val sourceType = makeMealType(id = 0, hour = 8, endHour = 10)
        sut.mealTypes.value = listOf(sourceType, makeMealType(id = 1, hour = 11, endHour = 14))
        val today = sut.selectedDay.value.atZone(ZoneId.systemDefault())
        val otherDayOfMonth = if (today.dayOfMonth == 1) 2 else 1
        sut.copyTargetDay.value = today.withDayOfMonth(otherDayOfMonth).toInstant()
        sut.copyTargetMealTypeId.value = "1"

        sut.onCopyConfirmed(listOf(source), sourceType)

        assertSingleDayReload(range, month, sut.copyTargetDay.value)
        assertEquals("the displayed day is not the one the copy landed on", listOf("f1"), sut.foodsConsumed.value.map { it.id })
        assertTrue("the copy's calendar dot must appear", otherDayOfMonth in sut.activeDaysInMonth.value)
    }

    @Test
    fun onCopyConfirmed_whenTargetMonthIsNotCached_doesNotFetchAnything() = runTest {
        val source = makeFood(id = "f1", hour = 8)
        val month = FetchFoodsConsumedForMonthUseCaseSpy(resultsPerCall = listOf(listOf(source)))
        val range = FetchFoodsConsumedInRangeUseCaseSpy()
        val sut = makeSUT(fetchFoodsConsumedForMonth = month, fetchFoodsConsumedInRange = range)
        sut.onAppear()
        val sourceType = makeMealType(id = 0, hour = 8, endHour = 10)
        sut.mealTypes.value = listOf(sourceType, makeMealType(id = 1, hour = 11, endHour = 14))
        sut.copyTargetDay.value = ZonedDateTime.now().minusMonths(3).toInstant()
        sut.copyTargetMealTypeId.value = "1"

        sut.onCopyConfirmed(listOf(source), sourceType)

        assertEquals("an uncached month is loaded whole when the user first navigates to it", 1, month.months.size)
        assertTrue(range.ranges.isEmpty())
    }

    @Test
    fun onCopyConfirmed_whenTargetIsAnotherMonth_keepsTheDisplayedDay() = runTest {
        val source = makeFood(id = "f1", hour = 8)
        val sut = makeSUT(fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods = listOf(source, makeFood(id = "f2", hour = 12))))
        val sourceType = makeMealType(id = 0, hour = 8, endHour = 10)
        sut.mealTypes.value = listOf(sourceType, makeMealType(id = 1, hour = 11, endHour = 14))
        val displayedDay = ZonedDateTime.now().minusMonths(2).toInstant()
        sut.selectedDay.value = displayedDay
        sut.foodsConsumed.value = listOf(source)
        sut.copyTargetDay.value = Instant.now()
        sut.copyTargetMealTypeId.value = "1"

        sut.onCopyConfirmed(listOf(source), sourceType)

        assertEquals(displayedDay, sut.selectedDay.value)
        assertEquals(listOf("f1"), sut.foodsConsumed.value.map { it.id })
    }

    @Test
    fun onCopyConfirmed_whenCopyFails_showsAlertAndKeepsTheBoxOpenWithoutCheckmark() = runTest {
        val source = makeFood(id = "f1", hour = 8)
        val sut = makeSUT(copyFoodsConsumed = CopyFoodsConsumedUseCaseFake(shouldThrow = true))
        val sourceType = makeMealType(id = 0, hour = 8, endHour = 10)
        sut.mealTypes.value = listOf(sourceType, makeMealType(id = 1, hour = 11, endHour = 14))
        sut.copyTargetDay.value = sut.selectedDay.value
        sut.copyTargetMealTypeId.value = "1"
        sut.copyPopoverIndex.value = 0

        sut.onCopyConfirmed(listOf(source), sourceType)

        assertNotNull(sut.alertItem.value)
        assertEquals(0, sut.copyPopoverIndex.value)
        assertFalse(sut.showCopyCheckmark.value)
        assertFalse(sut.isCopying.value)
    }

    @Test
    fun onCopyConfirmed_whenTargetIsTheSource_doesNothing() = runTest {
        val source = makeFood(id = "f1", hour = 8)
        val sut = makeSUT(copyFoodsConsumed = CopyFoodsConsumedUseCaseFake(shouldThrow = true))
        val sourceType = makeMealType(id = 0, hour = 8, endHour = 10)
        sut.mealTypes.value = listOf(sourceType)
        sut.copyTargetDay.value = sut.selectedDay.value
        sut.copyTargetMealTypeId.value = "0"
        sut.copyPopoverIndex.value = 0

        sut.onCopyConfirmed(listOf(source), sourceType)

        assertNull(sut.alertItem.value)
        assertEquals(0, sut.copyPopoverIndex.value)
    }

    @Test
    fun onAppear_whenAnonymousWithLoggedFoodAndNeverShown_showsSpotlightAndStoresNow() = runTest {
        val store = SignInSpotlightStoreFake()
        val now = Instant.now()
        val sut = makeSpotlightSUT(store = store, now = now)

        sut.onAppear()

        assertTrue("an anonymous user's diary is lost with the device, so it must be warned", sut.isSignInSpotlightVisible.value)
        assertEquals(now, store.lastShownAt)
    }

    @Test
    fun onAppear_whenSignedIn_neverShowsSpotlight() = runTest {
        val store = SignInSpotlightStoreFake()
        val sut = makeSpotlightSUT(store = store, authProvider = AuthProviderFake(isAnonymous = false))

        sut.onAppear()

        assertFalse("a signed-in user's diary is already safe, so the prompt would be noise", sut.isSignInSpotlightVisible.value)
        assertNull(store.lastShownAt)
    }

    @Test
    fun onAppear_whenNoFoodLogged_doesNotShowSpotlight() = runTest {
        val sut = makeSpotlightSUT(foods = emptyList())

        sut.onAppear()

        assertFalse("there is nothing to lose yet, and the spotlight on a first launch is noise", sut.isSignInSpotlightVisible.value)
    }

    @Test
    fun onFoodConsumedUpdated_whenFirstFoodIsLogged_showsSpotlightRightAway() = runTest {
        val store = SignInSpotlightStoreFake()
        val now = Instant.now()
        var loggedFoods = emptyList<FoodConsumedDomain>()
        val fetchForMonth = object : FetchFoodsConsumedForMonthUseCaseProtocol {
            override suspend fun invoke(month: Instant) = emptyList<FoodConsumedDomain>()
        }
        val fetchInRange = object : FetchFoodsConsumedInRangeUseCaseProtocol {
            override suspend fun invoke(from: Instant, to: Instant) = loggedFoods
        }
        val sut = makeSpotlightSUT(
            store = store,
            fetchFoodsConsumedForMonth = fetchForMonth,
            fetchFoodsConsumedInRange = fetchInRange,
            now = now,
        )
        sut.onAppear()
        loggedFoods = listOf(makeFood(id = "f1", hour = 9))

        sut.onFoodConsumedUpdated()

        assertTrue("the first logged food is the moment to warn, not the next time the app is opened", sut.isSignInSpotlightVisible.value)
        assertEquals(now, store.lastShownAt)
    }

    @Test
    fun onForeground_whenOnlyEmptyDaysAreCached_doesNotShowSpotlight() = runTest {
        val store = SignInSpotlightStoreFake()
        val sut = makeSpotlightSUT(store = store, foods = emptyList())
        sut.onAppear()

        sut.onForeground()

        assertFalse("an empty day cached as [] is not a logged food, and showing now would burn the week before the first food", sut.isSignInSpotlightVisible.value)
        assertNull(store.lastShownAt)
    }

    @Test
    fun onRefresh_whenShownSixDaysAgo_doesNotShowSpotlight() = runTest {
        val now = Instant.now()
        val sut = makeSpotlightSUT(store = SignInSpotlightStoreFake(now.minus(6, ChronoUnit.DAYS)), now = now)

        sut.onAppear()
        sut.onRefresh()

        assertFalse("the spotlight repeats weekly, not more often", sut.isSignInSpotlightVisible.value)
    }

    @Test
    fun onAppear_whenShownSevenDaysAgo_showsSpotlight() = runTest {
        val now = Instant.now()
        val store = SignInSpotlightStoreFake(now.minus(7, ChronoUnit.DAYS))
        val sut = makeSpotlightSUT(store = store, now = now)

        sut.onAppear()

        assertTrue("a process that stays alive for days must still show it once the week has passed", sut.isSignInSpotlightVisible.value)
        assertEquals(now, store.lastShownAt)
    }

    @Test
    fun onAppear_whenAnyPresentationIsActive_doesNotShowSpotlightOrBurnTheWeek() = runTest {
        val presentations: List<Pair<String, (DashboardViewModel) -> Unit>> = listOf(
            "settings" to { it.showSettings.value = true },
            "add food" to { it.showAddFoodSheet.value = true },
            "calendar" to { it.showCalendarSheet.value = true },
            "account" to { it.showAccountSheet.value = true },
            "alert" to { it.alertItem.value = AlertItem(titleRes = R.string.common_error_unknown) },
            "delete confirmation" to { it.isDeleteConfirmationVisible.value = true },
            "copy popover" to { it.copyPopoverIndex.value = 0 },
            "macro popover" to { it.macroPopoverIndex.value = 0 },
        )
        for ((name, present) in presentations) {
            val store = SignInSpotlightStoreFake()
            val sut = makeSpotlightSUT(store = store)
            present(sut)

            sut.onAppear()

            assertFalse("$name is up, and a second presentation would be dropped", sut.isSignInSpotlightVisible.value)
            assertNull("a dropped presentation must not burn the week ($name)", store.lastShownAt)
        }
    }

    @Test
    fun onSignInSpotlightSignInTapped_hidesSpotlightAndOpensAccountSheet() = runTest {
        val sut = makeSpotlightSUT()
        sut.onAppear()

        sut.onSignInSpotlightSignInTapped()

        assertFalse(sut.isSignInSpotlightVisible.value)
        assertTrue("the bubble is a way into the existing sign-in flow, not a new one", sut.showAccountSheet.value)
    }

    @Test
    fun onSignInSpotlightDismissed_hidesSpotlightWithoutOpeningAccountSheetOrChangingLastShown() = runTest {
        val store = SignInSpotlightStoreFake()
        val now = Instant.now()
        val sut = makeSpotlightSUT(store = store, now = now)
        sut.onAppear()

        sut.onSignInSpotlightDismissed()

        assertFalse(sut.isSignInSpotlightVisible.value)
        assertFalse(sut.showAccountSheet.value)
        assertEquals("a dismissed spotlight still counts as shown", now, store.lastShownAt)
    }

    @Test
    fun onAppear_whenFetchFails_doesNotShowSpotlight() = runTest {
        val sut = makeSpotlightSUT(fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCaseFake(shouldThrow = true))

        sut.onAppear()

        assertFalse("the spotlight must not appear over an error alert", sut.isSignInSpotlightVisible.value)
    }

    // MARK: - Helpers

    private fun firstOfCurrentMonth(): Instant = todayAt(12).atZone(ZoneId.systemDefault()).withDayOfMonth(1).toInstant()

    private class FetchFoodsConsumedForMonthUseCaseSpy(
        private val resultsPerCall: List<List<FoodConsumedDomain>> = listOf(emptyList()),
    ) : FetchFoodsConsumedForMonthUseCaseProtocol {
        val months = mutableListOf<Instant>()

        override suspend fun invoke(month: Instant): List<FoodConsumedDomain> {
            val result = resultsPerCall[minOf(months.size, resultsPerCall.lastIndex)]
            months += month
            return result
        }
    }

    private class FetchFoodsConsumedInRangeUseCaseSpy(
        private val result: List<FoodConsumedDomain> = emptyList(),
    ) : FetchFoodsConsumedInRangeUseCaseProtocol {
        val ranges = mutableListOf<Pair<Instant, Instant>>()

        override suspend fun invoke(from: Instant, to: Instant): List<FoodConsumedDomain> {
            ranges += from to to
            return result
        }
    }

    private class FetchFoodsConsumedInRangeUseCaseSuspending : FetchFoodsConsumedInRangeUseCaseProtocol {
        val gate = CompletableDeferred<List<FoodConsumedDomain>>()

        override suspend fun invoke(from: Instant, to: Instant): List<FoodConsumedDomain> = gate.await()
    }

    private fun startOfDay(day: Instant): Instant = day.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun makeSingleMealType() = FetchMealTypesUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 0, endHour = 23)))

    private fun assertSingleDayReload(
        range: FetchFoodsConsumedInRangeUseCaseSpy,
        month: FetchFoodsConsumedForMonthUseCaseSpy,
        day: Instant,
    ) {
        assertEquals("exactly one day query", listOf(startOfDay(day) to startOfDay(day)), range.ranges)
        assertEquals("only onAppear may load the month", 1, month.months.size)
    }

    private fun makeSUT(
        fetchMealTypes: FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseProtocol = FetchFoodsConsumedForMonthUseCaseFake(),
        fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseProtocol = FetchFoodsConsumedInRangeUseCaseFake(),
        setupDefaultMeals: SetupDefaultMealsUseCaseProtocol = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseProtocol = ConfirmMealTypesEmptyUseCaseFake(stubbedResult = true),
        deleteFoodConsumed: DeleteFoodConsumedUseCaseProtocol = DeleteFoodConsumedUseCaseFake(),
        copyFoodsConsumed: CopyFoodsConsumedUseCaseProtocol = CopyFoodsConsumedUseCaseFake(),
        authProvider: AuthProviderProtocol = AuthProviderFake(),
        signInSpotlightStore: SignInSpotlightStoreProtocol = SignInSpotlightStoreFake(),
        now: Instant = Instant.now(),
    ): DashboardViewModel = DashboardViewModel(
        fetchMealTypes = fetchMealTypes,
        fetchFoodsConsumedForMonth = fetchFoodsConsumedForMonth,
        fetchFoodsConsumedInRange = fetchFoodsConsumedInRange,
        setupDefaultMeals = setupDefaultMeals,
        confirmMealTypesEmpty = confirmMealTypesEmpty,
        deleteFoodConsumed = deleteFoodConsumed,
        copyFoodsConsumed = copyFoodsConsumed,
        authProvider = authProvider,
        signInSpotlightStore = signInSpotlightStore,
        now = { now },
    )

    private fun makeSpotlightSUT(
        store: SignInSpotlightStoreFake = SignInSpotlightStoreFake(),
        authProvider: AuthProviderProtocol = AuthProviderFake(),
        foods: List<FoodConsumedDomain> = listOf(makeFood(id = "f1", hour = 9)),
        fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseProtocol = FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods = foods),
        fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseProtocol = FetchFoodsConsumedInRangeUseCaseFake(),
        now: Instant = Instant.now(),
    ): DashboardViewModel = makeSUT(
        fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = listOf(makeMealType(id = 0, hour = 0, endHour = 23))),
        fetchFoodsConsumedForMonth = fetchFoodsConsumedForMonth,
        fetchFoodsConsumedInRange = fetchFoodsConsumedInRange,
        authProvider = authProvider,
        signInSpotlightStore = store,
        now = now,
    )

    private fun todayAt(hour: Int, minute: Int = 0): Instant {
        val zone = ZoneId.systemDefault()
        return Instant.now().atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toInstant()
    }

    private fun makeMealType(id: Int, hour: Int, endHour: Int, minute: Int = 0) = MealTypeDomain(
        id = "$id",
        name = "Meal $id",
        startMinutes = hour * 60 + minute,
        endMinutes = endHour * 60 + minute,
    )

    private fun makeMealTypeExcludingNow(id: Int, offsetMinutes: Int = 120): MealTypeDomain {
        val start = (Instant.now().minutesSinceMidnight() + offsetMinutes) % 1440
        return MealTypeDomain(id = "$id", name = "Meal $id", startMinutes = start, endMinutes = (start + 60) % 1440)
    }

    private fun makeFood(id: String, hour: Int, minute: Int = 0, fiber: Double? = 1.0, mealTypeId: String? = null) = FoodConsumedDomain(
        id = id,
        foodItemId = id,
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = "Jídlo",
        engName = "Food",
        weight = 100.0,
        date = todayAt(hour, minute),
        calories = 200,
        caloriesPerHundredGrams = 200.0,
        energyKJ = 837.0,
        protein = 10.0,
        carbohydrate = 20.0,
        carbohydrateSugar = 5.0,
        fat = 5.0,
        fatSaturated = 1.0,
        fatUnsaturated = 2.0,
        fiber = fiber,
        salt = 0.2,
        mealTypeId = mealTypeId,
    )
}
