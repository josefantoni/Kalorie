package antoni.kalorie.features.mycreatedmeal

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.usecases.CreateMyCreatedMealUseCaseFake
import antoni.kalorie.core.usecases.CreateMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemsByIdsUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemsByIdsUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCaseFake
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCaseProtocol
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyCreatedMealEditorViewModelTest {

    // MARK: - canSave

    @Test
    fun canSave_withNoIngredients_isFalse() {
        val sut = makeSUT()

        assertFalse(sut.canSave)
    }

    @Test
    fun canSave_withNameAndIngredientWithValidGrams_isTrue() {
        val sut = makeSUT()
        sut.name.value = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.setGrams(index = 0, text = "50")

        assertTrue(sut.canSave)
    }

    @Test
    fun canSave_withUnparseableGrams_isFalse() {
        val sut = makeSUT()
        sut.name.value = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.setGrams(index = 0, text = "")

        assertFalse(sut.canSave)
    }

    @Test
    fun canSave_whenEditingWithNoChanges_isFalse() {
        val sut = makeSUT(existingMeal = makeExistingMeal())

        assertFalse("opening an existing meal for editing must not enable Save until the user actually changes something", sut.canSave)
    }

    @Test
    fun canSave_whenEditingWithNameChanged_isTrue() {
        val sut = makeSUT(existingMeal = makeExistingMeal())
        sut.name.value = "Jiná kaše"

        assertTrue(sut.canSave)
    }

    @Test
    fun canSave_whenEditingWithGramsChanged_isTrue() {
        val sut = makeSUT(existingMeal = makeExistingMeal())
        sut.setGrams(index = 0, text = "75")

        assertTrue(sut.canSave)
    }

    // MARK: - onSelectSearchResult / onDeleteIngredient

    @Test
    fun onSelectSearchResult_appendsIngredientWithoutNavigating() {
        val sut = makeSUT()

        sut.onSelectSearchResult(makeFoodItem(id = "1"))
        sut.onSelectSearchResult(makeFoodItem(id = "2"))

        assertEquals(listOf("1", "2"), sut.ingredients.value.map { it.item.id })
    }

    @Test
    fun onSelectSearchResult_clearsSearchTextAndResults() = runTest {
        val sut = makeSUT(searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = listOf(makeFoodItem())))
        sut.searchText.value = "ovesné"
        sut.onSearchTextChanged()
        assertFalse(sut.searchResults.value.isEmpty())

        sut.onSelectSearchResult(makeFoodItem())

        assertEquals("picking a result should not leave the query visible, or the results section open, underneath the row it just added", "", sut.searchText.value)
        assertTrue(sut.searchResults.value.isEmpty())
    }

    @Test
    fun onSelectSearchResult_returnsTheNewDraftsId() {
        val sut = makeSUT()

        val id = sut.onSelectSearchResult(makeFoodItem())

        assertEquals("the view needs the new row's id to move keyboard focus onto it", sut.ingredients.value.last().id, id)
    }

    @Test
    fun onDeleteIngredient_removesRowAtOffset() {
        val sut = makeSUT()
        sut.onSelectSearchResult(makeFoodItem(id = "1"))
        sut.onSelectSearchResult(makeFoodItem(id = "2"))

        sut.onDeleteIngredient(setOf(0))

        assertEquals(listOf("2"), sut.ingredients.value.map { it.item.id })
    }

    // MARK: - onScannerButtonTapped / onBarcodeScanned

    @Test
    fun onScannerButtonTapped_makesScannerVisible() {
        val sut = makeSUT()

        sut.onScannerButtonTapped()

        assertTrue(sut.isScannerVisible.value)
    }

    @Test
    fun onBarcodeScanned_withEmptyBarcode_doesNothing() = runTest {
        val sut = makeSUT()
        sut.lastScannedBarcode.value = ""

        sut.onBarcodeScanned()

        assertNull(sut.alertItem.value)
        assertTrue(sut.ingredients.value.isEmpty())
    }

    @Test
    fun onBarcodeScanned_whenNotFoundLocallyOrExternally_showsNotFoundAlert() = runTest {
        val sut = makeSUT()
        sut.lastScannedBarcode.value = "8594004428464"

        sut.onBarcodeScanned()

        assertEquals(R.string.addFood_error_barcodeNotFound, sut.alertItem.value?.titleRes)
        assertTrue(sut.ingredients.value.isEmpty())
    }

    @Test
    fun onBarcodeScanned_whenLocalFound_appendsIngredientAndHidesScanner() = runTest {
        val item = makeFoodItem(id = "8594004428464")
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item))
        sut.isScannerVisible.value = true
        sut.lastScannedBarcode.value = "8594004428464"

        sut.onBarcodeScanned()

        assertEquals(listOf("8594004428464"), sut.ingredients.value.map { it.item.id })
        assertFalse(sut.isScannerVisible.value)
        assertEquals(
            "the view needs this to move keyboard focus onto the new row, same as a tapped search result",
            sut.ingredients.value.first().id,
            sut.scannedIngredientId.value,
        )
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onBarcodeScanned_whenOnlyExternalFound_appendsIngredientAndHidesScanner() = runTest {
        val item = makeFoodItem(id = "8594004428464")
        val sut = makeSUT(fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem = item))
        sut.isScannerVisible.value = true
        sut.lastScannedBarcode.value = "8594004428464"

        sut.onBarcodeScanned()

        assertEquals(listOf("8594004428464"), sut.ingredients.value.map { it.item.id })
        assertFalse(sut.isScannerVisible.value)
    }

    @Test
    fun onBarcodeScanned_whenExternalFails_showsLoadFailedAlertAndDoesNotAppend() = runTest {
        val sut = makeSUT(fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(shouldThrow = true))
        sut.lastScannedBarcode.value = "8594004428464"

        sut.onBarcodeScanned()

        assertEquals(R.string.addFood_error_loadFailed, sut.alertItem.value?.titleRes)
        assertTrue(sut.ingredients.value.isEmpty())
    }

    // MARK: - onSearchTextChanged (external fallback)

    @Test
    fun onSearchTextChanged_whenLocalEmptyAndQueryLongEnough_fallsBackToExternal() = runTest {
        val sut = makeSUT(
            searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = emptyList()),
            searchFoodExternally = SearchFoodExternallyUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "off-1"))),
        )
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertEquals(
            "a food only on OpenFoodFacts must still be reachable as an ingredient",
            listOf("off-1"),
            sut.externalSearchResults.value.map { it.id },
        )
    }

    @Test
    fun onSearchTextChanged_whenLocalResultsExist_doesNotFallBackToExternal() = runTest {
        val sut = makeSUT(
            searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = listOf(makeFoodItem())),
            searchFoodExternally = SearchFoodExternallyUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "off-1"))),
        )
        sut.searchText.value = "ovesné"

        sut.onSearchTextChanged()

        assertTrue(
            "the catalogue already answered the query, so hitting OpenFoodFacts on top would be a wasted network call",
            sut.externalSearchResults.value.isEmpty(),
        )
    }

    // MARK: - onGramsFieldDefocused

    @Test
    fun onGramsFieldDefocused_withEmptyGrams_fillsDefaultOfHundred() {
        val sut = makeSUT()
        val id = sut.onSelectSearchResult(makeFoodItem())

        sut.onGramsFieldDefocused(id)

        assertEquals(
            "leaving a freshly added row untouched should fall back to a sane default instead of blocking Save with no explanation",
            "100",
            sut.ingredients.value.first().gramsText,
        )
    }

    @Test
    fun onGramsFieldDefocused_withUserEnteredGrams_doesNotOverwrite() {
        val sut = makeSUT()
        val id = sut.onSelectSearchResult(makeFoodItem())
        sut.setGrams(index = 0, text = "50")

        sut.onGramsFieldDefocused(id)

        assertEquals("50", sut.ingredients.value.first().gramsText)
    }

    // MARK: - onSaveTapped

    @Test
    fun onSaveTapped_whenCannotSave_doesNotShowConfirmation() {
        val sut = makeSUT()

        sut.onSaveTapped()

        assertFalse(sut.isSaveConfirmationVisible.value)
    }

    @Test
    fun onSaveTapped_whenCanSave_showsConfirmation() {
        val sut = makeSUT()
        fillValidMeal(sut)

        sut.onSaveTapped()

        assertTrue(sut.isSaveConfirmationVisible.value)
    }

    // MARK: - onSaveConfirmed (create)

    @Test
    fun onSaveConfirmed_whenCreating_createsMealAndDismisses() = runTest {
        var onSavedCalled = false
        val sut = makeSUT(onSaved = { onSavedCalled = true })
        fillValidMeal(sut)

        sut.onSaveConfirmed()

        assertTrue(onSavedCalled)
        assertTrue(sut.shouldDismiss.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSaveConfirmed_whenEmbeddedWithDismissesOnSaveFalse_savesButLeavesTheHostInPlace() = runTest {
        var onSavedCalled = false
        val sut = makeSUT(onSaved = { onSavedCalled = true }, dismissesOnSave = false)
        fillValidMeal(sut)

        sut.onSaveConfirmed()

        assertTrue("the host (the add-food sheet) still needs to know the meal was saved", onSavedCalled)
        assertFalse("embedded in a sheet, dismissing would close the whole sheet rather than just this mode", sut.shouldDismiss.value)
    }

    @Test
    fun onSaveConfirmed_whenCreateFails_showsAlertAndDoesNotDismiss() = runTest {
        val sut = makeSUT(createMyCreatedMeal = CreateMyCreatedMealUseCaseFake(shouldThrow = true))
        fillValidMeal(sut)

        sut.onSaveConfirmed()

        assertNotNull(sut.alertItem.value)
        assertFalse(sut.shouldDismiss.value)
    }

    // MARK: - onSaveConfirmed (edit)

    @Test
    fun onSaveConfirmed_whenEditing_preservesCreatedAt() = runTest {
        var updatedMeal: MyCreatedMealDomain? = null
        val originalCreatedAt = Instant.ofEpochSecond(1_000)
        val existingMeal = MyCreatedMealDomain(
            id = "meal-1",
            name = "Kaše",
            ingredients = listOf(makeIngredientDomain()),
            createdAt = originalCreatedAt,
            updatedAt = originalCreatedAt,
        )
        val sut = makeSUT(
            updateMyCreatedMeal = object : UpdateMyCreatedMealUseCaseProtocol {
                override suspend fun invoke(meal: MyCreatedMealDomain) {
                    updatedMeal = meal
                }
            },
            existingMeal = existingMeal,
        )

        sut.onSaveConfirmed()

        assertEquals("meal-1", updatedMeal?.id)
        assertEquals(originalCreatedAt, updatedMeal?.createdAt)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun isEditing_reflectsWhetherAnExistingMealWasPassed() {
        assertFalse(makeSUT().isEditing)
        assertTrue(makeSUT(existingMeal = makeExistingMeal()).isEditing)
    }

    // MARK: - onAppear (catalogue refresh)

    @Test
    fun onAppear_whenCatalogueWasCorrected_updatesIngredientAndMarksMealAsChanged() = runTest {
        val sut = makeSUT(
            fetchFoodItemsByIds = FetchFoodItemsByIdsUseCaseFake(stubbedItems = listOf(makeFoodItem(calories = 140.0))),
            existingMeal = makeExistingMeal(),
        )
        assertFalse(sut.hasChanges)

        sut.onAppear()

        assertEquals(140.0, sut.ingredients.value.first().item.caloriesPerHundredGrams, 0.0)
        assertTrue(sut.hasChanges)
    }

    @Test
    fun onAppear_keepsTheEnteredGrams() = runTest {
        val sut = makeSUT(
            fetchFoodItemsByIds = FetchFoodItemsByIdsUseCaseFake(stubbedItems = listOf(makeFoodItem(calories = 140.0))),
            existingMeal = makeExistingMeal(),
        )
        val gramsBefore = sut.ingredients.value.first().gramsText

        sut.onAppear()

        assertEquals(gramsBefore, sut.ingredients.value.first().gramsText)
    }

    @Test
    fun onAppear_whenNothingChanged_doesNotMarkMealAsChanged() = runTest {
        val sut = makeSUT(
            fetchFoodItemsByIds = FetchFoodItemsByIdsUseCaseFake(stubbedItems = listOf(makeFoodItem())),
            existingMeal = makeExistingMeal(),
        )

        sut.onAppear()

        assertFalse(sut.hasChanges)
    }

    @Test
    fun onAppear_whenIngredientIsNotInCatalogue_keepsItsSnapshot() = runTest {
        val sut = makeSUT(
            fetchFoodItemsByIds = FetchFoodItemsByIdsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "other", calories = 1.0))),
            existingMeal = makeExistingMeal(),
        )

        sut.onAppear()

        assertEquals(155.0, sut.ingredients.value.first().item.caloriesPerHundredGrams, 0.0)
        assertFalse(sut.hasChanges)
    }

    @Test
    fun onAppear_whenFetchFails_keepsSnapshotsUntouched() = runTest {
        val sut = makeSUT(
            fetchFoodItemsByIds = FetchFoodItemsByIdsUseCaseFake(stubbedItems = listOf(makeFoodItem(calories = 140.0)), shouldThrow = true),
            existingMeal = makeExistingMeal(),
        )

        sut.onAppear()

        assertEquals(155.0, sut.ingredients.value.first().item.caloriesPerHundredGrams, 0.0)
        assertFalse(sut.hasChanges)
    }

    @Test
    fun onAppear_whenCreatingNewMeal_doesNotFetch() = runTest {
        val sut = makeSUT(
            fetchFoodItemsByIds = FetchFoodItemsByIdsUseCaseFake(stubbedItems = listOf(makeFoodItem(calories = 140.0)), shouldThrow = true),
        )

        sut.onAppear()

        assertTrue(sut.ingredients.value.isEmpty())
    }

    // MARK: - Helpers

    private fun fillValidMeal(sut: MyCreatedMealEditorViewModel) {
        sut.name.value = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.setGrams(index = 0, text = "50")
    }

    private fun MyCreatedMealEditorViewModel.setGrams(index: Int, text: String) {
        ingredients.value = ingredients.value.toMutableList().also { it[index] = it[index].copy(gramsText = text) }
    }

    private fun makeExistingMeal(): MyCreatedMealDomain = MyCreatedMealDomain(
        id = "meal-1",
        name = "Kaše",
        ingredients = listOf(makeIngredientDomain()),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun makeSUT(
        searchFoodItems: SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        searchFoodExternally: SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        fetchFoodItemsByIds: FetchFoodItemsByIdsUseCaseProtocol = FetchFoodItemsByIdsUseCaseFake(),
        createMyCreatedMeal: CreateMyCreatedMealUseCaseProtocol = CreateMyCreatedMealUseCaseFake(),
        updateMyCreatedMeal: UpdateMyCreatedMealUseCaseProtocol = UpdateMyCreatedMealUseCaseFake(),
        existingMeal: MyCreatedMealDomain? = null,
        onSaved: () -> Unit = {},
        dismissesOnSave: Boolean = true,
    ): MyCreatedMealEditorViewModel = MyCreatedMealEditorViewModel(
        searchFoodItems = searchFoodItems,
        searchFoodExternally = searchFoodExternally,
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally,
        fetchFoodItemsByIds = fetchFoodItemsByIds,
        createMyCreatedMeal = createMyCreatedMeal,
        updateMyCreatedMeal = updateMyCreatedMeal,
        existingMeal = existingMeal,
        onSaved = onSaved,
        dismissesOnSave = dismissesOnSave,
    )

    private fun makeFoodItem(id: String = "12345", calories: Double = 155.0): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = "Ovesné vločky",
        engName = "Oats",
        weight = 100.0,
        date = Instant.now(),
        energyKJ = 648.0,
        caloriesPerHundredGrams = calories,
        fat = 10.0,
        fatSaturated = 3.0,
        fatUnsaturatedFattyAcids = 3.0,
        carbohydrate = 1.0,
        carbohydratePureSugar = 0.0,
        fiber = 0.0,
        protein = 13.0,
        salt = 0.3,
    )

    private fun makeIngredientDomain(): MyCreatedMealIngredientDomain = MyCreatedMealIngredientDomain(
        foodItemId = "12345",
        czName = "Ovesné vločky",
        engName = "Oats",
        grams = 50.0,
        nutrition = FoodNutritionValues(
            energyKJ = 648.0,
            caloriesPerHundredGrams = 155.0,
            fat = 10.0,
            fatSaturated = 3.0,
            fatUnsaturatedFattyAcids = 3.0,
            carbohydrate = 1.0,
            carbohydratePureSugar = 0.0,
            fiber = 0.0,
            protein = 13.0,
            salt = 0.3,
        ),
    )
}
