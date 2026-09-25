package antoni.kalorie.features.dashboard

import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseFake
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.IsFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.IsFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseProtocol
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

    // MARK: - Favourites

    @Test
    fun onAppear_whenCatalogueItemNoLongerResolves_disablesAddingButKeepsButtonVisible() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = null))

        sut.onAppear()

        assertFalse(sut.isFavourite.value)
        assertFalse(
            "with no catalogue item to snapshot, adding must stay disabled rather than crash or write garbage",
            sut.canToggleFavourite,
        )
    }

    @Test
    fun onFavouriteToggled_whenAlreadyFavouriteAndCatalogueItemMissing_stillAllowsUnfavouriting() = runTest {
        val sut = makeSUT(
            isFavouriteFood = IsFavouriteFoodUseCaseFake(stubbedResult = true),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = null),
        )
        sut.onAppear()
        assertTrue(sut.isFavourite.value)
        assertTrue("removal needs only the id, so it must stay possible even when the catalogue item vanished", sut.canToggleFavourite)

        sut.onFavouriteToggled()

        assertFalse(sut.isFavourite.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onFavouriteToggled_whenNotFavouriteAndCatalogueItemMissing_revertsAndShowsAlert() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = null))
        sut.onAppear()
        assertFalse(sut.isFavourite.value)

        sut.onFavouriteToggled()

        assertFalse("adding needs the catalogue item, so a missing snapshot must not leave the toggle stuck on", sut.isFavourite.value)
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun onAppear_whenFoodItemKindIsExternalAndAlreadyFavourite_showsButtonRegardlessOfExternalLookup() = runTest {
        val sut = makeSUT(
            food = makeFood(kind = FoodItemKind.EXTERNAL),
            isFavouriteFood = IsFavouriteFoodUseCaseFake(stubbedResult = true),
            fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem = null),
        )

        sut.onAppear()

        assertTrue("an external item is favouritable via its own id, independent of the catalogue", sut.isFavourite.value)
        assertTrue(
            "already being a favourite must show the button even when the external lookup finds nothing",
            sut.canShowFavouriteButton,
        )
    }

    @Test
    fun onAppear_whenFoodItemKindIsExternalAndNotYetFavourite_showsFavouriteButtonFromExternalLookup() = runTest {
        val sut = makeSUT(
            food = makeFood(kind = FoodItemKind.EXTERNAL),
            fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem = makeCatalogueItem()),
        )

        sut.onAppear()

        assertFalse(sut.isFavourite.value)
        assertTrue(
            "an OpenFoodFacts item is favouritable before it is favourited too; it has no Firestore catalogue entry, so it is resolved via OpenFoodFacts instead",
            sut.canShowFavouriteButton,
        )
    }

    @Test
    fun onAppear_whenFoodItemKindIsCreatedMeal_skipsFavouriteAndCatalogueLookups() = runTest {
        val sut = makeSUT(
            food = makeFood(kind = FoodItemKind.CREATED_MEAL),
            isFavouriteFood = IsFavouriteFoodUseCaseFake(stubbedResult = true),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeCatalogueItem()),
        )

        sut.onAppear()

        assertFalse("a created meal has no catalogue counterpart to favourite, so the Dashboard never offers it", sut.isFavourite.value)
        assertFalse(sut.canShowFavouriteButton)
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
    fun onSave_whenWeightIsSavedButAssignFails_stillNotifiesSoTheDashboardShowsTheNewWeight() = runTest {
        val breakfast = MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 360, endMinutes = 600)
        var didNotify = false
        val sut = makeSUT(
            mealTypes = listOf(breakfast),
            assignFoodMealType = AssignFoodMealTypeUseCaseFake(shouldThrow = true),
            onFoodUpdated = { didNotify = true },
        )
        sut.weight.value = 150.0
        sut.onMealTypeSelected("breakfast")

        sut.onSave()

        assertTrue("the weight write already reached Firestore, so the dashboard must reload", didNotify)
        assertNotNull(sut.alertItem.value)
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
        isFavouriteFood: IsFavouriteFoodUseCaseProtocol = IsFavouriteFoodUseCaseFake(),
        addFavouriteFood: AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeCatalogueItem()),
        fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        onFoodUpdated: () -> Unit = {},
    ): FoodConsumedDetailViewModel =
        FoodConsumedDetailViewModel(
            food = food ?: makeFood(),
            mealTypes = mealTypes,
            updateFoodConsumed = updateFoodConsumed,
            assignFoodMealType = assignFoodMealType,
            fetchMealTypes = fetchMealTypes ?: FetchMealTypesUseCaseFake(stubbedTypes = mealTypes),
            isFavouriteFood = isFavouriteFood,
            addFavouriteFood = addFavouriteFood,
            removeFavouriteFood = removeFavouriteFood,
            fetchFoodItemByBarcode = fetchFoodItemByBarcode,
            fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally,
            onFoodUpdated = onFoodUpdated,
        )

    private fun makeCatalogueItem(): FoodItemDomain = FoodItemDomain(
        id = "12345",
        kind = FoodItemKind.CATALOGUE,
        czName = "Ovesné vločky",
        engName = "Oats",
        weight = 100.0,
        date = Instant.now(),
        energyKJ = 1500.0,
        caloriesPerHundredGrams = 370.0,
        fat = 7.0,
        fatSaturated = 1.0,
        fatUnsaturatedFattyAcids = 6.0,
        carbohydrate = 65.0,
        carbohydratePureSugar = 1.0,
        fiber = 10.0,
        protein = 13.0,
        salt = 0.0,
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
