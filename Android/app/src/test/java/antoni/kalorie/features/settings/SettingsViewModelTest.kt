package antoni.kalorie.features.settings

import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.CreateMealTypeUseCaseFake
import antoni.kalorie.core.usecases.CreateMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteMealTypeUseCaseFake
import antoni.kalorie.core.usecases.DeleteMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseFake
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCaseFake
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCaseProtocol
import antoni.kalorie.core.utils.minutesSinceMidnight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun onMove_movingFirstItemToLast_keepsTimeSlotsAtPositions() {
        val meal0 = makeMealType(id = 0, name = "A", hour = 8, endHour = 12)
        val meal1 = makeMealType(id = 1, name = "B", hour = 12, endHour = 16)
        val meal2 = makeMealType(id = 2, name = "C", hour = 16, endHour = 20)
        val sut = makeSUT(mealTypes = listOf(meal0, meal1, meal2))

        sut.onMove(from = 0, to = 3)

        val result = sut.mealTypes.value
        assertEquals("B", result[0].name)
        assertEquals(meal0.startMinutes, result[0].startMinutes)
        assertEquals("C", result[1].name)
        assertEquals(meal1.startMinutes, result[1].startMinutes)
        assertEquals("A", result[2].name)
        assertEquals(meal2.startMinutes, result[2].startMinutes)
    }

    @Test
    fun onMove_movingLastItemToFirst_keepsTimeSlotsAtPositions() {
        val meal0 = makeMealType(id = 0, name = "A", hour = 8, endHour = 12)
        val meal1 = makeMealType(id = 1, name = "B", hour = 12, endHour = 16)
        val meal2 = makeMealType(id = 2, name = "C", hour = 16, endHour = 20)
        val sut = makeSUT(mealTypes = listOf(meal0, meal1, meal2))

        sut.onMove(from = 2, to = 0)

        val result = sut.mealTypes.value
        assertEquals("C", result[0].name)
        assertEquals(meal0.startMinutes, result[0].startMinutes)
        assertEquals("A", result[1].name)
        assertEquals(meal1.startMinutes, result[1].startMinutes)
        assertEquals("B", result[2].name)
        assertEquals(meal2.startMinutes, result[2].startMinutes)
    }

    @Test
    fun onMove_keepsDefaultKeyWithTheMovedMealType() {
        val keyed = MealTypeDomain(id = "0", name = "A", startMinutes = 8 * 60, endMinutes = 12 * 60, defaultKey = "breakfast")
        val sut = makeSUT(mealTypes = listOf(keyed, makeMealType(id = 1, name = "B", hour = 12, endHour = 16)))

        sut.onMove(from = 0, to = 2)

        val result = sut.mealTypes.value
        assertNull(result[0].defaultKey)
        assertEquals("a reorder must not turn a default meal into a user-named one", "breakfast", result[1].defaultKey)
    }

    @Test
    fun onMove_setsHasPendingReorder() {
        val sut = makeSUT(
            mealTypes = listOf(
                makeMealType(id = 0, name = "A", hour = 8, endHour = 12),
                makeMealType(id = 1, name = "B", hour = 12, endHour = 16),
            ),
        )

        assertFalse(sut.hasPendingReorder.value)
        sut.onMove(from = 0, to = 2)
        assertTrue(sut.hasPendingReorder.value)
    }

    @Test
    fun onSaveReorder_afterMove_clearsHasPendingReorder() = runTest {
        val sut = makeSUT(
            mealTypes = listOf(
                makeMealType(id = 0, name = "A", hour = 8, endHour = 12),
                makeMealType(id = 1, name = "B", hour = 12, endHour = 16),
            ),
        )
        sut.onMove(from = 0, to = 2)

        sut.onSaveReorder()

        assertFalse(sut.hasPendingReorder.value)
    }

    @Test
    fun onSaveReorder_whenUseCaseFails_stillClearsHasPendingReorder() = runTest {
        val sut = makeSUT(
            mealTypes = listOf(
                makeMealType(id = 0, name = "A", hour = 8, endHour = 12),
                makeMealType(id = 1, name = "B", hour = 12, endHour = 16),
            ),
            updateMealTypeTimes = UpdateMealTypeTimesUseCaseFake(shouldThrow = true),
        )
        sut.onMove(from = 0, to = 2)

        sut.onSaveReorder()

        assertFalse(sut.hasPendingReorder.value)
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onSaveReorder_withoutPendingReorder_doesNotInvokeUseCase() = runTest {
        val sut = makeSUT(
            mealTypes = listOf(makeMealType(id = 0, name = "A", hour = 8, endHour = 12)),
            updateMealTypeTimes = UpdateMealTypeTimesUseCaseFake(shouldThrow = true),
        )

        sut.onSaveReorder()

        assertNull(
            "a delete-only edit session has nothing to persist, so Done must be able to close the sheet without a failing network call",
            sut.alertItem.value,
        )
    }

    @Test
    fun onDelete_withSingleMealType_showsAlertAndKeepsIt() = runTest {
        val sut = makeSUT(mealTypes = listOf(makeMealType(id = 0, name = "A", hour = 8, endHour = 12)))

        sut.onDelete(index = 0)

        assertNotNull(sut.alertItem.value)
        assertEquals(1, sut.mealTypes.value.size)
    }

    @Test
    fun onDelete_withMultipleMealTypes_removesCorrectOne() = runTest {
        val sut = makeSUT(
            mealTypes = listOf(
                makeMealType(id = 0, name = "A", hour = 8, endHour = 12),
                makeMealType(id = 1, name = "B", hour = 12, endHour = 16),
            ),
        )

        sut.onDelete(index = 0)

        assertEquals(1, sut.mealTypes.value.size)
        assertEquals("1", sut.mealTypes.value[0].id)
    }

    @Test
    fun onDelete_whenUseCaseFails_showsAlertAndKeepsMealTypes() = runTest {
        val sut = makeSUT(
            mealTypes = listOf(
                makeMealType(id = 0, name = "A", hour = 8, endHour = 12),
                makeMealType(id = 1, name = "B", hour = 12, endHour = 16),
            ),
            deleteMealType = DeleteMealTypeUseCaseFake(shouldThrow = true),
        )

        sut.onDelete(index = 0)

        assertNotNull(sut.alertItem.value)
        assertEquals(2, sut.mealTypes.value.size)
    }

    @Test
    fun onShowAddForm_withExistingMealTypes_setsStartAfterLastEnd() {
        val meal = makeMealType(id = 0, name = "A", hour = 8, endHour = 12)
        val sut = makeSUT(mealTypes = listOf(meal))

        sut.onShowAddForm()

        assertTrue(sut.isAddFormVisible.value)
        assertEquals(meal.endMinutes, sut.newMealStart.value.minutesSinceMidnight())
    }

    @Test
    fun onShowAddForm_withNoMealTypes_makesFormVisible() {
        val sut = makeSUT(mealTypes = emptyList())

        sut.onShowAddForm()

        assertTrue(sut.isAddFormVisible.value)
    }

    @Test
    fun onAppear_whenMaintainerClaimIsTrue_setsIsMaintainer() = runTest {
        val sut = makeSUT(fetchMaintainerClaim = FetchMaintainerClaimUseCaseFake(stubbedIsMaintainer = true))

        sut.onAppear()

        assertTrue(sut.isMaintainer.value)
    }

    @Test
    fun onAppear_whenMaintainerClaimIsFalse_leavesIsMaintainerFalse() = runTest {
        val sut = makeSUT(fetchMaintainerClaim = FetchMaintainerClaimUseCaseFake(stubbedIsMaintainer = false))

        sut.onAppear()

        assertFalse(sut.isMaintainer.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        mealTypes: List<MealTypeDomain> = emptyList(),
        createMealType: CreateMealTypeUseCaseProtocol = CreateMealTypeUseCaseFake(),
        deleteMealType: DeleteMealTypeUseCaseProtocol = DeleteMealTypeUseCaseFake(),
        updateMealTypeTimes: UpdateMealTypeTimesUseCaseProtocol = UpdateMealTypeTimesUseCaseFake(),
        fetchMaintainerClaim: FetchMaintainerClaimUseCaseProtocol = FetchMaintainerClaimUseCaseFake(),
    ): SettingsViewModel = SettingsViewModel(
        mealTypes = mealTypes,
        createMealType = createMealType,
        deleteMealType = deleteMealType,
        updateMealTypeTimes = updateMealTypeTimes,
        fetchMaintainerClaim = fetchMaintainerClaim,
    )

    private fun makeMealType(id: Int, name: String, hour: Int, endHour: Int): MealTypeDomain = MealTypeDomain(id = "$id", name = name, startMinutes = hour * 60, endMinutes = endHour * 60)
}
