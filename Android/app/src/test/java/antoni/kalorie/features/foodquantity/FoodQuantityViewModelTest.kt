package antoni.kalorie.features.foodquantity

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCaseProtocol
import antoni.kalorie.core.utils.isLoading
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodQuantityViewModelTest {

    // MARK: - grams

    @Test
    fun grams_withOneHundredGramUnit_is100() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.HundredGrams
        sut.quantity.value = 1.0

        assertEquals(100.0, sut.grams, 0.0)
    }

    @Test
    fun grams_withTwoHundredGrams_is200() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.HundredGrams
        sut.quantity.value = 2.0

        assertEquals(200.0, sut.grams, 0.0)
    }

    @Test
    fun grams_withGramsUnit_equalsQuantity() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 150.0

        assertEquals(150.0, sut.grams, 0.0)
    }

    // MARK: - scaledCalories

    @Test
    fun scaledCalories_calculatesFromGrams() {
        val sut = makeSUT(item = makeFoodItem(caloriesPerHundredGrams = 200.0))
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 250.0

        assertEquals(500, sut.scaledCalories)
    }

    @Test
    fun scaledCalories_withOneHundredGramOf200kcalItem_is200() {
        val sut = makeSUT(item = makeFoodItem(caloriesPerHundredGrams = 200.0))
        sut.unit.value = FoodQuantityUnit.HundredGrams
        sut.quantity.value = 1.0

        assertEquals(200, sut.scaledCalories)
    }

    @Test
    fun savesRoundedCalories_whenScalingProducesFraction() {
        val sut = makeSUT(item = makeFoodItem(caloriesPerHundredGrams = 133.0))
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 150.0

        assertEquals(200, sut.scaledCalories)
    }

    @Test
    fun scaledCalories_whenCaloriesPerHundredGramsIsFractional_roundsOnce() {
        val sut = makeSUT(item = makeFoodItem(caloriesPerHundredGrams = 133.6))
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 150.0

        assertEquals(200, sut.scaledCalories)
    }

    // MARK: - scaledFiber

    @Test
    fun scaledFiber_whenItemsFiberIsUnknown_staysNil() {
        val sut = makeSUT(item = makeFoodItem(fiber = null))

        assertNull(sut.scaledFiber)
    }

    // MARK: - init defaults

    @Test
    fun init_withoutQuantityOrUnit_defaultsToOneHundredGram() {
        val sut = makeSUT()

        assertEquals(1.0, sut.quantity.value, 0.0)
        assertEquals(FoodQuantityUnit.HundredGrams, sut.unit.value)
    }

    @Test
    fun init_withExplicitQuantityAndUnit_usesThemInsteadOfTheDefault() {
        val sut = makeSUT(quantity = 100.0, unit = FoodQuantityUnit.Grams)

        assertEquals(100.0, sut.quantity.value, 0.0)
        assertEquals(FoodQuantityUnit.Grams, sut.unit.value)
    }

    // MARK: - onUnitSelected

    @Test
    fun onUnitSelected_toGrams_convertsQuantity() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.HundredGrams
        sut.quantity.value = 2.0

        sut.onUnitSelected(FoodQuantityUnit.Grams)

        assertEquals(200.0, sut.quantity.value, 0.0)
    }

    @Test
    fun onUnitSelected_toHundredGrams_convertsQuantity() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 200.0

        sut.onUnitSelected(FoodQuantityUnit.HundredGrams)

        assertEquals(2.0, sut.quantity.value, 0.0)
    }

    @Test
    fun onUnitSelected_toHundredGrams_keepsFraction() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 150.0

        sut.onUnitSelected(FoodQuantityUnit.HundredGrams)

        assertEquals(1.5, sut.quantity.value, 0.0)
    }

    @Test
    fun onUnitSelected_toHundredGrams_belowFiftyGrams_doesNotFloorToOne() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 30.0

        sut.onUnitSelected(FoodQuantityUnit.HundredGrams)

        assertEquals(0.3, sut.quantity.value, 1e-9)
    }

    @Test
    fun onUnitSelected_toHundredGramsAndBack_roundTripsExactly() {
        val sut = makeSUT()
        sut.unit.value = FoodQuantityUnit.Grams
        sut.quantity.value = 150.0

        sut.onUnitSelected(FoodQuantityUnit.HundredGrams)
        sut.onUnitSelected(FoodQuantityUnit.Grams)

        assertEquals(150.0, sut.quantity.value, 0.0)
    }

    @Test
    fun onUnitSelected_setsUnit() {
        val sut = makeSUT()
        val portion = FoodPortionDomain(name = "1 balení", grams = 250.0)

        sut.onUnitSelected(FoodQuantityUnit.Portion(portion))

        assertEquals(FoodQuantityUnit.Portion(portion), sut.unit.value)
    }

    // MARK: - onConfirm

    @Test
    fun onConfirm_withZeroQuantity_showsInvalidQuantityAlert() = runTest {
        val sut = makeSUT()
        sut.quantity.value = 0.0

        sut.onConfirm()

        assertEquals(R.string.foodQuantity_error_invalidQuantity, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onConfirm_whenSaveSucceeds_callsOnSaved() = runTest {
        var onSavedCalled = false
        val sut = makeSUT(onSaved = { onSavedCalled = true })

        sut.onConfirm()

        assertTrue(onSavedCalled)
    }

    @Test
    fun onConfirm_whenSaveSucceeds_setsLoadedState() = runTest {
        val sut = makeSUT()

        sut.onConfirm()

        assertFalse(sut.state.value.isLoading)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onConfirm_whenSaveFails_showsAlert() = runTest {
        val sut = makeSUT(saveFoodConsumed = SaveFoodConsumedUseCaseFake(shouldThrow = true))

        sut.onConfirm()

        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onConfirm_whenSaveFails_doesNotCallOnSaved() = runTest {
        var onSavedCalled = false
        val sut = makeSUT(saveFoodConsumed = SaveFoodConsumedUseCaseFake(shouldThrow = true), onSaved = { onSavedCalled = true })

        sut.onConfirm()

        assertFalse(onSavedCalled)
    }

    @Test
    fun onConfirm_usesFreshlyFetchedMealTypesInsteadOfStaleSnapshot() = runTest {
        val loggedAt = makeDate(hour = 12)
        val staleMealTypes = listOf(makeMealType(id = "breakfast", hour = 6, endHour = 10))
        val freshMealTypes = listOf(makeMealType(id = "lunch", hour = 11, endHour = 14))
        val spy = SaveFoodConsumedUseCaseSpy()
        val sut = makeSUT(
            saveFoodConsumed = spy,
            fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = freshMealTypes),
            selectedDate = loggedAt,
            mealTypes = staleMealTypes,
        )

        sut.onConfirm()

        assertEquals(
            "onConfirm must resolve the meal-type pin against meal types fetched at save time, not the list captured when the sheet was opened",
            "lunch",
            spy.capturedMealTypeId,
        )
    }

    @Test
    fun onConfirm_whenMealTypesRefetchFails_fallsBackToOriginalSnapshot() = runTest {
        val loggedAt = makeDate(hour = 8)
        val originalMealTypes = listOf(makeMealType(id = "breakfast", hour = 6, endHour = 10))
        val spy = SaveFoodConsumedUseCaseSpy()
        val sut = makeSUT(
            saveFoodConsumed = spy,
            fetchMealTypes = FetchMealTypesUseCaseFake(shouldThrow = true),
            selectedDate = loggedAt,
            mealTypes = originalMealTypes,
        )

        sut.onConfirm()

        assertEquals(
            "a failed refetch must fall back to the snapshot captured when the sheet was opened, not discard it",
            "breakfast",
            spy.capturedMealTypeId,
        )
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onConfirm_whenUserPickedAMealType_usesThatInsteadOfTheTimeBasedDefault() = runTest {
        val loggedAt = makeDate(hour = 12)
        val mealTypes = listOf(
            makeMealType(id = "lunch", hour = 11, endHour = 14),
            makeMealType(id = "dinner", hour = 18, endHour = 21),
        )
        val spy = SaveFoodConsumedUseCaseSpy()
        val sut = makeSUT(
            saveFoodConsumed = spy,
            fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = mealTypes),
            selectedDate = loggedAt,
            mealTypes = mealTypes,
        )
        sut.onMealTypeSelected("dinner")

        sut.onConfirm()

        assertEquals("an explicit pick overrides the time-of-day default, mirroring the edit screen's picker", "dinner", spy.capturedMealTypeId)
    }

    @Test
    fun onConfirm_whenPickedMealTypeNoLongerExistsAfterRefetch_showsAlertAndDoesNotSave() = runTest {
        val mealTypes = listOf(makeMealType(id = "lunch", hour = 11, endHour = 14))
        val spy = SaveFoodConsumedUseCaseSpy()
        val sut = makeSUT(
            saveFoodConsumed = spy,
            fetchMealTypes = FetchMealTypesUseCaseFake(stubbedTypes = emptyList()),
            mealTypes = mealTypes,
        )
        sut.onMealTypeSelected("lunch")

        sut.onConfirm()

        assertFalse("a pin to a meal type deleted since the sheet opened must not be silently written", spy.wasCalled)
        assertEquals(R.string.common_error_unknown, sut.alertItem.value?.titleRes)
    }

    // MARK: - selectedMealTypeId (init)

    @Test
    fun init_preselectsTheMealTypeResolvedFromTimeOfDay() {
        val sut = makeSUT(selectedDate = makeDate(hour = 12), mealTypes = listOf(makeMealType(id = "lunch", hour = 11, endHour = 14)))

        assertEquals("lunch", sut.selectedMealTypeId.value)
    }

    @Test
    fun init_whenNoWindowMatches_preselectsNoMealType() {
        val sut = makeSUT(selectedDate = makeDate(hour = 3), mealTypes = listOf(makeMealType(id = "breakfast", hour = 6, endHour = 10)))

        assertNull(sut.selectedMealTypeId.value)
    }

    // MARK: - unitOptions

    @Test
    fun unitOptions_ordersCataloguePortionBeforeGramsBeforeHundredGrams() {
        val slice = FoodPortionDomain(name = "1 plátek", grams = 30.0)
        val sut = makeSUT(item = makeFoodItem(portions = listOf(slice)))

        assertEquals(
            listOf(FoodQuantityUnit.Portion(slice), FoodQuantityUnit.Grams, FoodQuantityUnit.HundredGrams),
            sut.unitOptions,
        )
    }

    // MARK: - defaultUnit(for:)

    @Test
    fun defaultUnit_withCataloguePortions_returnsFirstPortion() {
        val firstPortion = FoodPortionDomain(name = "1 balení", grams = 80.0)
        val secondPortion = FoodPortionDomain(name = "1 plátek", grams = 30.0)
        val item = makeFoodItem(portions = listOf(firstPortion, secondPortion))

        assertEquals(
            "a catalogue-defined portion is the fastest way to log a packaged food, so it must be pre-selected",
            FoodQuantityUnit.Portion(firstPortion),
            FoodQuantityViewModel.defaultUnit(item),
        )
    }

    @Test
    fun defaultUnit_withoutCataloguePortions_returnsGrams() {
        val item = makeFoodItem(portions = emptyList())

        assertEquals(FoodQuantityUnit.Grams, FoodQuantityViewModel.defaultUnit(item))
    }

    // MARK: - onFavouriteToggled

    @Test
    fun onFavouriteToggled_whenNotFavourite_marksItAndReportsTheNewValueToTheSheet() = runTest {
        var reported: Pair<String, Boolean>? = null
        val sut = makeSUT(onFavouriteChanged = { id, isFavourite -> reported = id to isFavourite })

        sut.onFavouriteToggled()

        assertTrue(sut.isFavourite.value)
        assertEquals("test" to true, reported)
    }

    @Test
    fun onFavouriteToggled_whenFavourite_removesItAndReportsTheNewValueToTheSheet() = runTest {
        var reported: Pair<String, Boolean>? = null
        val sut = makeSUT(isFavourite = true, onFavouriteChanged = { id, isFavourite -> reported = id to isFavourite })

        sut.onFavouriteToggled()

        assertFalse(sut.isFavourite.value)
        assertEquals("test" to false, reported)
    }

    @Test
    fun onFavouriteToggled_whenWriteFails_revertsShowsAlertAndDoesNotReport() = runTest {
        var wasReported = false
        val sut = makeSUT(addFavouriteFood = AddFavouriteFoodUseCaseFake(shouldThrow = true), onFavouriteChanged = { _, _ -> wasReported = true })

        sut.onFavouriteToggled()

        assertFalse(sut.isFavourite.value)
        assertNotNull(sut.alertItem.value)
        assertFalse(wasReported)
        assertFalse(sut.isTogglingFavourite.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        item: FoodItemDomain = makeFoodItem(),
        saveFoodConsumed: SaveFoodConsumedUseCaseProtocol = SaveFoodConsumedUseCaseFake(),
        fetchMealTypes: FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        selectedDate: Instant = Instant.now(),
        mealTypes: List<MealTypeDomain> = emptyList(),
        isFavourite: Boolean = false,
        addFavouriteFood: AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        onSaved: () -> Unit = {},
        onFavouriteChanged: (String, Boolean) -> Unit = { _, _ -> },
        quantity: Double = 1.0,
        unit: FoodQuantityUnit = FoodQuantityUnit.HundredGrams,
    ): FoodQuantityViewModel = FoodQuantityViewModel(
        item = item,
        saveFoodConsumed = saveFoodConsumed,
        fetchMealTypes = fetchMealTypes,
        selectedDate = selectedDate,
        mealTypes = mealTypes,
        isFavourite = isFavourite,
        addFavouriteFood = addFavouriteFood,
        removeFavouriteFood = removeFavouriteFood,
        onSaved = onSaved,
        onFavouriteChanged = onFavouriteChanged,
        quantity = quantity,
        unit = unit,
    )

    private fun makeDate(hour: Int): Instant =
        ZonedDateTime.now().withHour(hour).withMinute(0).withSecond(0).withNano(0).toInstant()

    private fun makeMealType(id: String, hour: Int, endHour: Int): MealTypeDomain =
        MealTypeDomain(id = id, name = "Meal $id", startMinutes = hour * 60, endMinutes = endHour * 60)

    private fun makeFoodItem(
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
        caloriesPerHundredGrams: Double = 100.0,
        fiber: Double? = 0.0,
        portions: List<FoodPortionDomain> = emptyList(),
    ): FoodItemDomain = FoodItemDomain(
        id = "test",
        kind = kind,
        czName = "Tvaroh",
        engName = "Cottage cheese",
        weight = 100.0,
        date = Instant.now(),
        energyKJ = 400.0,
        caloriesPerHundredGrams = caloriesPerHundredGrams,
        fat = 2.0,
        fatSaturated = 1.0,
        fatUnsaturatedFattyAcids = 1.0,
        carbohydrate = 4.0,
        carbohydratePureSugar = 3.0,
        fiber = fiber,
        protein = 13.0,
        salt = 0.1,
        portions = portions,
    )
}

private class SaveFoodConsumedUseCaseSpy : SaveFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    var wasCalled = false
        private set
    var capturedMealTypeId: String? = null
        private set

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, grams: Double, date: Instant, mealTypeId: String?) {
        wasCalled = true
        capturedMealTypeId = mealTypeId
    }
}
