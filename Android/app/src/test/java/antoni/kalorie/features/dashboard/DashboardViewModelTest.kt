package antoni.kalorie.features.dashboard

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderError
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseFake
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseFake
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseProtocol
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.isSameDay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {

    // MARK: - DailyMacros

    @Test
    fun dailyMacros_whenAFoodsFiberIsUnknown_showsZeroInsteadOfExcludingIt() {
        val macros = DailyMacros(listOf(makeFood(id = "1", hour = 8, fiber = null), makeFood(id = "2", hour = 9, fiber = 3.0)))

        assertEquals(3.0, macros.fiber, 0.0)
    }

    // MARK: - groupedFoods — no foods

    @Test
    fun groupedFoods_withNoFoodsConsumed_returnsEmpty() {
        val sut = makeSUT()
        sut.mealTypes.value = listOf(makeMealType(id = 0, hour = 8, endHour = 12))
        sut.foodsConsumed.value = emptyList()

        assertTrue(sut.groupedFoods.isEmpty())
    }

    // MARK: - groupedFoods — assignment

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

    // MARK: - groupedFoods — ordering

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

    // MARK: - groupedFoods — pinning (ADR 0022)

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

    // MARK: - onAppear

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

    // MARK: - onRefresh

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

    // MARK: - delete

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
    fun onDeleteConfirmed_whenDeleteSucceeds_reloadsFoodsFromServer() = runTest {
        val remaining = makeFood(id = "f2", hour = 9)
        val toDelete = makeFood(id = "f1", hour = 8)
        val sut = makeSUT(fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods = listOf(remaining)))
        sut.foodsConsumed.value = listOf(toDelete, remaining)

        sut.onDeleteRequested(toDelete)
        sut.onDeleteConfirmed()

        assertEquals("a confirmed delete must refetch the day so the removed entry disappears", listOf("f2"), sut.foodsConsumed.value.map { it.id })
        assertNull(sut.alertItem.value)
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

    // MARK: - Helpers

    private fun makeSUT(
        fetchMealTypes: FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseProtocol = FetchFoodsConsumedForMonthUseCaseFake(),
        setupDefaultMeals: SetupDefaultMealsUseCaseProtocol = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseProtocol = ConfirmMealTypesEmptyUseCaseFake(stubbedResult = true),
        deleteFoodConsumed: DeleteFoodConsumedUseCaseProtocol = DeleteFoodConsumedUseCaseFake(),
    ): DashboardViewModel = DashboardViewModel(
        fetchMealTypes = fetchMealTypes,
        fetchFoodsConsumedForMonth = fetchFoodsConsumedForMonth,
        setupDefaultMeals = setupDefaultMeals,
        confirmMealTypesEmpty = confirmMealTypesEmpty,
        deleteFoodConsumed = deleteFoodConsumed,
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
