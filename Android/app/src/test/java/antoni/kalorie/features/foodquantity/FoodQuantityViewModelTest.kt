package antoni.kalorie.features.foodquantity

import antoni.kalorie.R
import antoni.kalorie.components.FoodPortionDraft
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemPersonalPortionsUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemPersonalPortionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.SaveFoodItemPersonalPortionsUseCaseFake
import antoni.kalorie.core.usecases.SaveFoodItemPersonalPortionsUseCaseProtocol
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCaseFake
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCaseProtocol
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

    // MARK: - onAppear (personal portions)

    @Test
    fun onAppear_withCatalogueItem_fetchesPersonalPortions() = runTest {
        val stubbedPortion = FoodPortionDomain(name = "1 balení", grams = 33.0)
        val sut = makeSUT(
            item = makeFoodItem(kind = FoodItemKind.CATALOGUE),
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(stubbedPortion)),
        )

        sut.onAppear()

        assertEquals(listOf(stubbedPortion), sut.personalPortions.value)
    }

    @Test
    fun onAppear_withPersonalPortions_movesDefaultToFirstPersonalPortion() = runTest {
        val cataloguePortion = FoodPortionDomain(name = "1 balení", grams = 250.0)
        val personalPortion = FoodPortionDomain(name = "1 hrnek", grams = 40.0)
        val item = makeFoodItem(portions = listOf(cataloguePortion))
        val sut = makeSUT(
            item = item,
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(personalPortion)),
            unit = FoodQuantityViewModel.defaultUnit(item),
        )

        sut.onAppear()

        assertEquals(
            "ADR 0030: the first option in the list is always the preselected unit, so once personal portions resolve the default follows them",
            FoodQuantityUnit.Portion(personalPortion),
            sut.unit.value,
        )
        assertEquals("the step-2 reselection must not be treated as a unit change and rescale the quantity", 1.0, sut.quantity.value, 0.0)
    }

    @Test
    fun onAppear_withPersonalPortionsAndNoCataloguePortion_resetsQuantityFromTheGramsFallbackDefault() = runTest {
        val personalPortion = FoodPortionDomain(name = "1 hrnek", grams = 40.0)
        val sut = makeSUT(
            item = makeFoodItem(portions = emptyList()),
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(personalPortion)),
            quantity = 100.0,
            unit = FoodQuantityUnit.Grams,
        )

        sut.onAppear()

        assertEquals(
            "a portion resolving into the plain-grams default (quantity 100, ADR 0030) must reset to 1, or the screen would open at 100 × 1 hrnek instead of 1 × 1 hrnek",
            1.0,
            sut.quantity.value,
            0.0,
        )
    }

    @Test
    fun onAppear_withoutPersonalPortions_keepsSynchronousDefault() = runTest {
        val cataloguePortion = FoodPortionDomain(name = "1 balení", grams = 250.0)
        val sut = makeSUT(item = makeFoodItem(portions = listOf(cataloguePortion)), unit = FoodQuantityUnit.Portion(cataloguePortion))

        sut.onAppear()

        assertEquals(FoodQuantityUnit.Portion(cataloguePortion), sut.unit.value)
    }

    @Test
    fun onAppear_withExternalItem_doesNotSurfacePersonalPortions() = runTest {
        val stubbedPortion = FoodPortionDomain(name = "1 balení", grams = 33.0)
        val sut = makeSUT(
            item = makeFoodItem(kind = FoodItemKind.EXTERNAL),
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(stubbedPortion)),
        )

        sut.onAppear()

        assertTrue(
            "OpenFoodFacts items have no reliable package size to key a personal portion off (design 0008)",
            sut.personalPortions.value.isEmpty(),
        )
        assertFalse(sut.isPersonalPortionsAvailable)
    }

    @Test
    fun onAppear_whenFetchFails_keepsTheSynchronousDefaultAndRaisesNoAlert() = runTest {
        val sut = makeSUT(fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(shouldThrow = true))

        sut.onAppear()

        assertTrue(sut.personalPortions.value.isEmpty())
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onUnitSelected_thenOnAppearResolvesPersonalPortions_doesNotOverrideUserChoice() = runTest {
        val cataloguePortion = FoodPortionDomain(name = "1 balení", grams = 250.0)
        val personalPortion = FoodPortionDomain(name = "1 hrnek", grams = 40.0)
        val sut = makeSUT(
            item = makeFoodItem(portions = listOf(cataloguePortion)),
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(personalPortion)),
        )
        sut.onUnitSelected(FoodQuantityUnit.Grams)

        sut.onAppear()

        assertEquals(
            "a personal portion resolving after the user already picked a unit must not override that choice",
            FoodQuantityUnit.Grams,
            sut.unit.value,
        )
    }

    // MARK: - onPortionsManagerOpened

    @Test
    fun onPortionsManagerOpened_seedsOneBlankDraftRegardlessOfCurrentGrams() {
        val sut = makeSUT(quantity = 1000.0, unit = FoodQuantityUnit.Grams)

        sut.onPortionsManagerOpened()

        assertEquals(listOf(""), sut.portionDrafts.value.map { it.name })
        assertEquals(
            "a portion is a reusable shortcut; the quantity being weighed right now (e.g. a whole loaf) is not its size",
            listOf(""),
            sut.portionDrafts.value.map { it.gramsText },
        )
    }

    // MARK: - portion drafts

    @Test
    fun arePortionDraftsComplete_isFalseUntilEveryDraftHasNameAndGrams() {
        val sut = makeSUT()
        assertFalse("the always-present empty row must keep the add button disabled", sut.arePortionDraftsComplete)

        sut.portionDrafts.value = listOf(sut.portionDrafts.value[0].copy(name = "1 balení"))
        assertFalse(sut.arePortionDraftsComplete)
        sut.portionDrafts.value = listOf(sut.portionDrafts.value[0].copy(gramsText = "33"))
        assertTrue(sut.arePortionDraftsComplete)
        sut.onAddPortionDraftTapped()

        assertFalse("a freshly added empty row must disable the add button again", sut.arePortionDraftsComplete)
    }

    @Test
    fun canSavePortionDrafts_isTrueWhenAtLeastOneDraftIsCompleteEvenIfAnotherIsEmpty() {
        val sut = makeSUT()
        assertFalse(sut.canSavePortionDrafts)
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = "1 balení", gramsText = "33"))
        sut.onAddPortionDraftTapped()

        assertTrue(
            "the row added with the plus button is still empty, but the completed row above it is unsaved and must be savable",
            sut.canSavePortionDrafts,
        )
    }

    @Test
    fun onSavePersonalPortions_skipsEmptyDrafts() = runTest {
        val sut = makeSUT()
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = "1 balení", gramsText = "33"), FoodPortionDraft.blank)

        sut.onSavePersonalPortions()

        assertEquals(listOf(FoodPortionDomain(name = "1 balení", grams = 33.0)), sut.personalPortions.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onDeletePortionDraft_whenDeletingTheLastDraft_leavesOneEmptyDraft() {
        val sut = makeSUT()
        sut.portionDrafts.value = listOf(sut.portionDrafts.value[0].copy(name = "1 balení"))

        sut.onDeletePortionDraft(sut.portionDrafts.value[0])

        assertEquals("the screen must always show at least one row", listOf(""), sut.portionDrafts.value.map { it.name })
    }

    // MARK: - onSavePersonalPortions

    @Test
    fun onSavePersonalPortions_whenSaveSucceeds_appendsAllDraftsAndResetsToOneEmptyDraft() = runTest {
        val sut = makeSUT()
        sut.portionDrafts.value = listOf(
            FoodPortionDraft(name = "1 balení", gramsText = "33"),
            FoodPortionDraft(name = "1 lžíce", gramsText = "15"),
        )

        sut.onSavePersonalPortions()

        assertEquals(
            listOf(FoodPortionDomain(name = "1 balení", grams = 33.0), FoodPortionDomain(name = "1 lžíce", grams = 15.0)),
            sut.personalPortions.value,
        )
        assertNull(sut.alertItem.value)
        assertEquals(listOf(""), sut.portionDrafts.value.map { it.name })
        assertEquals(listOf(""), sut.portionDrafts.value.map { it.gramsText })
    }

    @Test
    fun onSavePersonalPortions_whenSaveFails_restoresPreAddSnapshotEvenWithADuplicateNameAndGrams() = runTest {
        val existing = FoodPortionDomain(name = "1 lžíce", grams = 15.0)
        val sut = makeSUT(
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(existing)),
            saveFoodItemPersonalPortions = SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow = true),
        )
        sut.onAppear()
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = existing.name, gramsText = "15"))

        sut.onSavePersonalPortions()

        assertEquals(
            "a failed save must restore the pre-add snapshot, not filter by (name, grams) equality — a value-based filter also drops an already-saved portion that happens to share the new one's name and grams",
            listOf(existing),
            sut.personalPortions.value,
        )
        assertEquals(R.string.myPortions_error_saveFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onSavePersonalPortions_whenGramsFieldIsBlank_showsAlertAndDoesNotSave() = runTest {
        val sut = makeSUT()
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = "1 balení", gramsText = ""))

        sut.onSavePersonalPortions()

        assertTrue("an unparseable grams field must not silently save a zero-gram portion", sut.personalPortions.value.isEmpty())
        assertEquals(R.string.foodPortion_error_invalidGrams, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onSavePersonalPortions_acceptsACommaAsTheDecimalSeparator() = runTest {
        val sut = makeSUT()
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = "1 lžíce", gramsText = "7,5"))

        sut.onSavePersonalPortions()

        assertEquals(listOf(FoodPortionDomain(name = "1 lžíce", grams = 7.5)), sut.personalPortions.value)
    }

    // MARK: - onDeletePersonalPortion

    @Test
    fun onDeletePersonalPortion_whenSaveSucceeds_removesPortion() = runTest {
        val portion = FoodPortionDomain(name = "1 balení", grams = 33.0)
        val sut = makeSUT(fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(portion)))
        sut.onAppear()

        sut.onDeletePersonalPortion(portion)

        assertTrue(sut.personalPortions.value.isEmpty())
    }

    @Test
    fun onDeletePersonalPortion_whenSaveFails_restoresPortionAndShowsAlert() = runTest {
        val portion = FoodPortionDomain(name = "1 balení", grams = 33.0)
        val sut = makeSUT(
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(portion)),
            saveFoodItemPersonalPortions = SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow = true),
        )
        sut.onAppear()

        sut.onDeletePersonalPortion(portion)

        assertEquals(listOf(portion), sut.personalPortions.value)
        assertEquals(R.string.myPortions_error_deleteFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onDeletePersonalPortion_whenPortionIsSelectedUnit_switchesToGramsKeepingAmount() = runTest {
        val portion = FoodPortionDomain(name = "1 hrnek", grams = 40.0)
        val sut = makeSUT(fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(portion)))
        sut.onAppear()
        sut.quantity.value = 2.0

        sut.onDeletePersonalPortion(portion)

        assertEquals("a deleted portion is no longer a picker option, so it cannot stay selected", FoodQuantityUnit.Grams, sut.unit.value)
        assertEquals("removing a shortcut must not change the amount the user is about to log", 80.0, sut.grams, 0.0)
    }

    @Test
    fun onDeletePersonalPortion_whenSaveFails_restoresSelectedUnit() = runTest {
        val portion = FoodPortionDomain(name = "1 hrnek", grams = 40.0)
        val sut = makeSUT(
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(portion)),
            saveFoodItemPersonalPortions = SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow = true),
        )
        sut.onAppear()

        sut.onDeletePersonalPortion(portion)

        assertEquals(FoodQuantityUnit.Portion(portion), sut.unit.value)
        assertEquals(1.0, sut.quantity.value, 0.0)
    }

    @Test
    fun unitOptions_ordersPersonalPortionsBeforeCataloguePortionsAndGenericUnits() = runTest {
        val cataloguePortion = FoodPortionDomain(name = "1 balení", grams = 250.0)
        val personalPortion = FoodPortionDomain(name = "1 hrnek", grams = 40.0)
        val sut = makeSUT(
            item = makeFoodItem(portions = listOf(cataloguePortion)),
            fetchFoodItemPersonalPortions = FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions = listOf(personalPortion)),
        )

        sut.onAppear()

        assertEquals(
            "ADR 0030: the user's own shortcuts lead the list, ahead of the item's canonical portions",
            listOf(
                FoodQuantityUnit.Portion(personalPortion),
                FoodQuantityUnit.Portion(cataloguePortion),
                FoodQuantityUnit.Grams,
                FoodQuantityUnit.HundredGrams,
            ),
            sut.unitOptions,
        )
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

    // MARK: - My created meal portions

    @Test
    fun unitOptions_forMeal_listsEachPortionOnce() {
        val portion = FoodPortionDomain(name = "1 miska", grams = 250.0)
        val meal = makeMeal(portions = listOf(portion))
        val sut = makeSUT(item = meal.asFoodItem(), meal = meal)

        assertEquals(
            "the meal's portions live on the meal itself, so item.portions must not be listed a second time",
            listOf(FoodQuantityUnit.Portion(portion), FoodQuantityUnit.Grams, FoodQuantityUnit.HundredGrams),
            sut.unitOptions,
        )
    }

    @Test
    fun onSavePersonalPortions_forMeal_writesPortionsToMealAndNotifiesParent() = runTest {
        val existing = FoodPortionDomain(name = "1 miska", grams = 250.0)
        val meal = makeMeal(portions = listOf(existing))
        var notifiedMeal: MyCreatedMealDomain? = null
        val sut = makeSUT(item = meal.asFoodItem(), meal = meal, onMealUpdated = { notifiedMeal = it })
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = "1 lžíce", gramsText = "15"))

        sut.onSavePersonalPortions()

        val expected = listOf(existing, FoodPortionDomain(name = "1 lžíce", grams = 15.0))
        assertEquals(expected, sut.personalPortions.value)
        assertEquals("the parent list must see the new portion, or reopening the meal would lose it", expected, notifiedMeal?.portions)
    }

    @Test
    fun onSavePersonalPortions_forMeal_whenSaveFails_restoresPortionsAndDoesNotNotifyParent() = runTest {
        val existing = FoodPortionDomain(name = "1 miska", grams = 250.0)
        val meal = makeMeal(portions = listOf(existing))
        var wasNotified = false
        val sut = makeSUT(
            item = meal.asFoodItem(),
            meal = meal,
            updateMyCreatedMeal = UpdateMyCreatedMealUseCaseFake(shouldThrow = true),
            onMealUpdated = { wasNotified = true },
        )
        sut.portionDrafts.value = listOf(FoodPortionDraft(name = "1 lžíce", gramsText = "15"))

        sut.onSavePersonalPortions()

        assertEquals(listOf(existing), sut.personalPortions.value)
        assertFalse(wasNotified)
        assertEquals(R.string.myPortions_error_saveFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onDeletePersonalPortion_forMeal_removesPortionFromMealAndNotifiesParent() = runTest {
        val portion = FoodPortionDomain(name = "1 miska", grams = 250.0)
        val meal = makeMeal(portions = listOf(portion))
        var notifiedMeal: MyCreatedMealDomain? = null
        val sut = makeSUT(item = meal.asFoodItem(), meal = meal, onMealUpdated = { notifiedMeal = it })

        sut.onDeletePersonalPortion(portion)

        assertTrue(sut.personalPortions.value.isEmpty())
        assertEquals(emptyList<FoodPortionDomain>(), notifiedMeal?.portions)
    }

    // MARK: - Helpers

    private fun makeMeal(portions: List<FoodPortionDomain>): MyCreatedMealDomain = MyCreatedMealDomain(
        id = "meal",
        name = "Guláš",
        ingredients = listOf(
            MyCreatedMealIngredientDomain(
                foodItemId = "beef",
                czName = "Hovězí",
                engName = "Beef",
                grams = 200.0,
                nutrition = FoodNutritionValues(
                    energyKJ = 1000.0,
                    caloriesPerHundredGrams = 240.0,
                    fat = 15.0,
                    fatSaturated = 6.0,
                    fatUnsaturatedFattyAcids = 9.0,
                    carbohydrate = 0.0,
                    carbohydratePureSugar = 0.0,
                    fiber = 0.0,
                    protein = 26.0,
                    salt = 0.1,
                ),
            ),
        ),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        portions = portions,
    )

    private fun makeSUT(
        item: FoodItemDomain = makeFoodItem(),
        saveFoodConsumed: SaveFoodConsumedUseCaseProtocol = SaveFoodConsumedUseCaseFake(),
        fetchMealTypes: FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        selectedDate: Instant = Instant.now(),
        mealTypes: List<MealTypeDomain> = emptyList(),
        isFavourite: Boolean = false,
        addFavouriteFood: AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseProtocol = FetchFoodItemPersonalPortionsUseCaseFake(),
        saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseProtocol = SaveFoodItemPersonalPortionsUseCaseFake(),
        meal: MyCreatedMealDomain? = null,
        updateMyCreatedMeal: UpdateMyCreatedMealUseCaseProtocol = UpdateMyCreatedMealUseCaseFake(),
        onSaved: () -> Unit = {},
        onMealUpdated: (MyCreatedMealDomain) -> Unit = {},
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
        fetchFoodItemPersonalPortions = fetchFoodItemPersonalPortions,
        saveFoodItemPersonalPortions = saveFoodItemPersonalPortions,
        meal = meal,
        updateMyCreatedMeal = updateMyCreatedMeal,
        onSaved = onSaved,
        onMealUpdated = onMealUpdated,
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
