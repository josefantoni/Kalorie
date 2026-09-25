package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCaseFake
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCaseFake
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseFake
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.R
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFoodSheetViewModelTest {

    // MARK: - mode

    @Test
    fun mode_startsInSearch() {
        assertEquals(AddFoodSheetMode.SEARCH, makeSUT().mode.value)
    }

    @Test
    fun onModeSelected_switchesModeAndClosesTheScanner() {
        val sut = makeSUT(isScannerVisible = true)

        sut.onModeSelected(AddFoodSheetMode.CREATE_MEAL)

        assertEquals(AddFoodSheetMode.CREATE_MEAL, sut.mode.value)
        assertFalse(sut.isScannerVisible.value)
    }

    // MARK: - displayedResults

    @Test
    fun displayedResults_hoistsMatchingFavouritesAboveCreatedMealsAndCatalog() = runTest {
        val sut = makeSUT(
            fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "fav", czName = "Ovar"))),
            fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(stubbedMeals = listOf(makeMeal(id = "meal", name = "Ovesná kaše"))),
        )
        sut.onAppear()
        sut.localFoodItems.value = listOf(makeFoodItem(id = "cat", czName = "Ovoce"))
        sut.searchText.value = "ov"

        assertEquals(listOf("fav", "meal", "cat"), sut.displayedResults.map { it.id })
    }

    @Test
    fun displayedResults_createdMeal_hasCreatedMealKind() = runTest {
        val sut = makeSUT(fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(stubbedMeals = listOf(makeMeal(id = "meal", name = "Ovesná kaše"))))
        sut.onAppear()
        sut.searchText.value = "ov"

        assertEquals(FoodItemKind.CREATED_MEAL, sut.displayedResults.first { it.id == "meal" }.kind)
    }

    // MARK: - isMyCreatedMeal

    @Test
    fun isMyCreatedMeal_returnsTrueOnlyForCreatedMealKind() {
        val sut = makeSUT()

        assertTrue(sut.isMyCreatedMeal(makeFoodItem(kind = FoodItemKind.CREATED_MEAL)))
        assertFalse(sut.isMyCreatedMeal(makeFoodItem(kind = FoodItemKind.CATALOGUE)))
        assertFalse(sut.isMyCreatedMeal(makeFoodItem(kind = FoodItemKind.EXTERNAL)))
    }

    // MARK: - onDeleteMealConfirmed

    @Test
    fun onDeleteMealConfirmed_removesRowOptimistically() = runTest {
        val sut = makeSUT(
            fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(stubbedMeals = listOf(makeMeal(id = "1", name = "A"), makeMeal(id = "2", name = "B"))),
        )
        sut.onAppear()
        sut.onDeleteMealRequested(sut.myCreatedMeals.value[0])

        sut.onDeleteMealConfirmed()

        assertEquals(listOf("2"), sut.myCreatedMeals.value.map { it.id })
    }

    @Test
    fun onDeleteMealConfirmed_whenDeleteFails_restoresRowAndShowsAlert() = runTest {
        val sut = makeSUT(
            fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(stubbedMeals = listOf(makeMeal(id = "1", name = "A"), makeMeal(id = "2", name = "B"))),
            deleteMyCreatedMeal = DeleteMyCreatedMealUseCaseFake(shouldThrow = true),
        )
        sut.onAppear()
        sut.onDeleteMealRequested(sut.myCreatedMeals.value[0])

        sut.onDeleteMealConfirmed()

        assertEquals("a failed delete must restore the row at its original position", listOf("1", "2"), sut.myCreatedMeals.value.map { it.id })
        assertEquals(R.string.myCreatedMeal_error_deleteFailed, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onDeleteMealConfirmed_withoutAPendingRequest_doesNothing() = runTest {
        val sut = makeSUT(fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(stubbedMeals = listOf(makeMeal(id = "1", name = "A"))))
        sut.onAppear()

        sut.onDeleteMealConfirmed()

        assertEquals(listOf("1"), sut.myCreatedMeals.value.map { it.id })
    }

    // MARK: - onMyCreatedMealSaved

    @Test
    fun onMyCreatedMealSaved_returnsToSearchWithTheNewMealImmediatelyLoggable() = runTest {
        val sut = makeSUT(
            fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(stubbedMeals = listOf(makeMeal(id = "new-meal", name = "Ovesná kaše"))),
        )
        sut.onModeSelected(AddFoodSheetMode.CREATE_MEAL)

        sut.onMyCreatedMealSaved()

        assertEquals(AddFoodSheetMode.SEARCH, sut.mode.value)
        assertEquals("a meal just composed must be loggable without reopening the sheet", listOf("new-meal"), sut.myCreatedMeals.value.map { it.id })
    }

    // MARK: - onScannerButtonTapped

    @Test
    fun onScannerButtonTapped_makesScannerVisible() {
        val sut = makeSUT()
        sut.onScannerButtonTapped()
        assertTrue(sut.isScannerVisible.value)
        assertEquals(AddFoodSheetMode.SEARCH, sut.mode.value)
    }

    @Test
    fun onScannerButtonTapped_whenScannerAlreadyVisible_keepsScannerVisible() {
        val sut = makeSUT(isScannerVisible = true)
        sut.onScannerButtonTapped()
        assertTrue(sut.isScannerVisible.value)
    }

    // MARK: - onScenePhaseActive

    @Test
    fun onScenePhaseActive_whenScannerVisibleAndCameraStillAvailable_keepsScannerVisible() {
        val sut = makeSUT(isScannerVisible = true)
        sut.onScenePhaseActive(isCameraAvailable = true)
        assertTrue(sut.isScannerVisible.value)
    }

    @Test
    fun onScenePhaseActive_whenScannerVisibleAndCameraNoLongerAvailable_hidesScannerAndShowsAlert() {
        val sut = makeSUT(isScannerVisible = true)
        sut.onScenePhaseActive(isCameraAvailable = false)
        assertFalse(sut.isScannerVisible.value)
        assertEquals(R.string.addFood_camera_permissionAlert, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onScenePhaseActive_whenScannerNotVisible_doesNothing() {
        val sut = makeSUT(isScannerVisible = false)
        sut.onScenePhaseActive(isCameraAvailable = false)
        assertFalse(sut.isScannerVisible.value)
        assertNull(sut.alertItem.value)
    }

    // MARK: - onBarcodeScanned

    @Test
    fun onBarcodeScanned_withEmptyBarcode_doesNothing() = runTest {
        val sut = makeSUT()
        sut.lastScannedBarcode.value = ""
        sut.onBarcodeScanned()
        assertNull(sut.alertItem.value)
        assertFalse(sut.isBarcodeSearchLoading.value)
    }

    @Test
    fun onBarcodeScanned_whenNotFound_showsNotFoundAlert() = runTest {
        val sut = makeSUT()
        sut.lastScannedBarcode.value = "8594004428464"
        sut.onBarcodeScanned()
        assertEquals(R.string.addFood_error_barcodeNotFound, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onBarcodeScanned_whenNotFound_stopsLoadingAndClearsTheScannedCode() = runTest {
        val sut = makeSUT()
        sut.lastScannedBarcode.value = "8594004428464"
        sut.onBarcodeScanned()
        assertFalse(sut.isBarcodeSearchLoading.value)
        assertEquals("the same code must be deliverable again", "", sut.lastScannedBarcode.value)
    }

    @Test
    fun onBarcodeScanned_whenLocalFound_navigatesToQuantityView() = runTest {
        val item = makeFoodItem(id = "8594004428464")
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item), isScannerVisible = true)
        sut.lastScannedBarcode.value = "8594004428464"
        sut.onBarcodeScanned()
        assertTrue(sut.isPushedToQuantityView.value)
        assertFalse(sut.isScannerVisible.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onBarcodeScanned_whenExternalFound_navigatesToQuantityView() = runTest {
        val item = makeFoodItem(id = "8594004428464")
        val sut = makeSUT(fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem = item), isScannerVisible = true)
        sut.lastScannedBarcode.value = "8594004428464"
        sut.onBarcodeScanned()
        assertTrue(sut.isPushedToQuantityView.value)
        assertFalse(sut.isScannerVisible.value)
    }

    @Test
    fun onBarcodeScanned_whenExternalFails_showsLoadFailedAlert() = runTest {
        val sut = makeSUT(fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(shouldThrow = true))
        sut.lastScannedBarcode.value = "8594004428464"
        sut.onBarcodeScanned()
        assertEquals(R.string.addFood_error_loadFailed, sut.alertItem.value?.titleRes)
    }

    // MARK: - onSearchTextChanged

    @Test
    fun onSearchTextChanged_whenLocalSearchFails_localItemsAreEmptyAndNoErrorIsRaised() = runTest {
        val sut = makeSUT(searchFoodItems = SearchFoodItemsUseCaseFake(shouldThrow = true))
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertTrue(sut.localFoodItems.value.isEmpty())
    }

    @Test
    fun onSearchTextChanged_whenLocalSearchFails_dropsResultsOfThePreviousQuery() = runTest {
        val sut = makeSUT(searchFoodItems = SearchFoodItemsUseCaseFake(shouldThrow = true))
        sut.localFoodItems.value = listOf(makeFoodItem(id = "stale-local"))
        sut.externalFoodItems.value = listOf(makeFoodItem(id = "stale-external"))
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertTrue("stale results would read as results for the query the user typed last", sut.localFoodItems.value.isEmpty())
        assertTrue(sut.externalFoodItems.value.isEmpty())
    }

    @Test
    fun onSearchTextChanged_withResults_publishesThemAsDisplayedResults() = runTest {
        val sut = makeSUT(searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "abc"))))
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertEquals(listOf("abc"), sut.displayedResults.map { it.id })
    }

    @Test
    fun onSearchTextChanged_withEmptyText_clearsTheResults() = runTest {
        val sut = makeSUT(searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "abc"))))
        sut.searchText.value = "tvaroh"
        sut.onSearchTextChanged()

        sut.searchText.value = ""
        sut.onSearchTextChanged()

        assertTrue(sut.localFoodItems.value.isEmpty())
    }

    // MARK: - onSelectFoodItem

    @Test
    fun onSelectFoodItem_setsSelectedFoodItemAndNavigates() {
        val sut = makeSUT()

        sut.onSelectFoodItem(makeFoodItem(id = "abc"))

        assertEquals("abc", sut.selectedFoodItem.value?.id)
        assertTrue(sut.isPushedToQuantityView.value)
    }

    @Test
    fun onSelectFoodItem_whenCalledTwice_firstItemWins() {
        val sut = makeSUT()

        sut.onSelectFoodItem(makeFoodItem(id = "A"))
        sut.onSelectFoodItem(makeFoodItem(id = "B"))

        assertEquals("A", sut.selectedFoodItem.value?.id)
        assertTrue(sut.isPushedToQuantityView.value)
    }

    @Test
    fun onSearchTextChanged_whenAlreadyPushedToQuantityView_doesNotSearch() = runTest {
        val sut = makeSUT(searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "abc"))))
        sut.onSelectFoodItem(makeFoodItem(id = "A"))
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertFalse(sut.localFoodItems.value.isNotEmpty())
    }

    @Test
    fun onSearchTextChanged_whenExternalSearchFails_externalItemsAreEmptyAndNoErrorIsRaised() = runTest {
        val sut = makeSUT(searchFoodExternally = SearchFoodExternallyUseCaseFake(shouldThrow = true))
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertTrue(sut.externalFoodItems.value.isEmpty())
        assertFalse(sut.isExternalSearchLoading.value)
    }

    @Test
    fun onSearchTextChanged_whenNothingLocalMatchesAndTextHasThreeCharacters_publishesExternalResults() = runTest {
        val sut = makeSUT(searchFoodExternally = SearchFoodExternallyUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "ext"))))
        sut.searchText.value = "tvo"

        sut.onSearchTextChanged()

        assertEquals(listOf("ext"), sut.externalFoodItems.value.map { it.id })
    }

    @Test
    fun onSearchTextChanged_whenTextIsShorterThanThreeCharacters_doesNotSearchExternally() = runTest {
        val sut = makeSUT(searchFoodExternally = SearchFoodExternallyUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "ext"))))
        sut.searchText.value = "tv"

        sut.onSearchTextChanged()

        assertTrue(sut.externalFoodItems.value.isEmpty())
    }

    @Test
    fun onSearchTextChanged_whenALocalResultMatches_doesNotSearchExternally() = runTest {
        val sut = makeSUT(
            searchFoodItems = SearchFoodItemsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "local"))),
            searchFoodExternally = SearchFoodExternallyUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "ext"))),
        )
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertTrue("one local match is treated as sufficient, so the network fallback is skipped", sut.externalFoodItems.value.isEmpty())
    }

    @Test
    fun onSearchTextChanged_withEmptyText_clearsExternalResults() = runTest {
        val sut = makeSUT(searchFoodExternally = SearchFoodExternallyUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "ext"))))
        sut.searchText.value = "tvaroh"
        sut.onSearchTextChanged()
        sut.searchText.value = ""

        sut.onSearchTextChanged()

        assertTrue(sut.externalFoodItems.value.isEmpty())
    }

    // MARK: - onSelectFavouriteFood

    @Test
    fun onSelectFavouriteFood_whenCatalogueCorrectedItem_selectsAndReplacesWithFreshItem() = runTest {
        val stale = makeFoodItem(id = "fav", czName = "Ovar")
        val corrected = makeFoodItem(id = "fav", czName = "Ovar opravený")
        val sut = makeSUT(
            fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(stale)),
            refreshFavouriteFood = RefreshFavouriteFoodUseCaseFake(stubbedItem = corrected),
        )
        sut.onAppear()

        sut.onSelectFavouriteFood(stale)

        assertEquals(corrected, sut.selectedFoodItem.value)
        assertEquals(listOf(corrected), sut.favouriteFoods.value)
        assertTrue(sut.isPushedToQuantityView.value)
    }

    @Test
    fun onSelectFavouriteFood_whenRefreshFails_stillSelectsTheStoredSnapshot() = runTest {
        val stale = makeFoodItem(id = "fav", czName = "Ovar")
        val sut = makeSUT(refreshFavouriteFood = RefreshFavouriteFoodUseCaseFake(shouldThrow = true))

        sut.onSelectFavouriteFood(stale)

        assertEquals(stale, sut.selectedFoodItem.value)
        assertTrue(sut.isPushedToQuantityView.value)
    }

    // MARK: - displayedResults

    @Test
    fun displayedResults_hoistsMatchingFavouritesAboveCatalog() = runTest {
        val sut = makeSUT(fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "fav", czName = "Ovar"))))
        sut.onAppear()
        sut.localFoodItems.value = listOf(makeFoodItem(id = "cat", czName = "Ovoce"))
        sut.searchText.value = "ov"

        assertEquals(listOf("fav", "cat"), sut.displayedResults.map { it.id })
    }

    @Test
    fun displayedResults_whenAFavouriteIsAlsoACatalogueMatch_listsItOnce() = runTest {
        val favourite = makeFoodItem(id = "fav", czName = "Ovar")
        val sut = makeSUT(fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(favourite)))
        sut.onAppear()
        sut.localFoodItems.value = listOf(makeFoodItem(id = "fav", czName = "Ovar"), makeFoodItem(id = "cat", czName = "Ovoce"))
        sut.searchText.value = "ov"

        assertEquals(listOf("fav", "cat"), sut.displayedResults.map { it.id })
    }

    @Test
    fun displayedResults_matchesFavouritesByLowercasedPrefixWithoutFoldingDiacritics() = runTest {
        val sut = makeSUT(fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(makeFoodItem(id = "fav", czName = "Řepa"))))
        sut.onAppear()
        sut.searchText.value = "repa"

        assertTrue("the local favourite match is a plain lowercased prefix, unlike the folded server search", sut.displayedResults.isEmpty())
    }

    // MARK: - onFavouriteChanged

    @Test
    fun onFavouriteChanged_whenFavourited_putsTheItemFirstAndMarksItFavourite() = runTest {
        val existing = makeFoodItem(id = "old")
        val sut = makeSUT(fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(existing)))
        sut.onAppear()
        val added = makeFoodItem(id = "new")

        sut.onFavouriteChanged(id = "new", isFavourite = true, item = added)

        assertEquals(listOf("new", "old"), sut.favouriteFoods.value.map { it.id })
        assertTrue(sut.isFavourite(added))
    }

    @Test
    fun onFavouriteChanged_whenUnfavourited_removesTheItemAndItsMark() = runTest {
        val existing = makeFoodItem(id = "old")
        val sut = makeSUT(fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(stubbedItems = listOf(existing)))
        sut.onAppear()

        sut.onFavouriteChanged(id = "old", isFavourite = false, item = existing)

        assertTrue(sut.favouriteFoods.value.isEmpty())
        assertFalse(sut.isFavourite(existing))
    }

    // MARK: - onFoodConsumedSaved

    @Test
    fun onFoodConsumedSaved_notifiesTheDashboardAndRequestsDismissal() {
        var onFoodSavedCalled = false
        val sut = AddFoodSheetViewModel(
            searchFoodItems = SearchFoodItemsUseCaseFake(),
            searchFoodExternally = SearchFoodExternallyUseCaseFake(),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
            fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(),
            fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(),
            refreshFavouriteFood = RefreshFavouriteFoodUseCaseFake(),
            fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(),
            deleteMyCreatedMeal = DeleteMyCreatedMealUseCaseFake(),
            onFoodSaved = { onFoodSavedCalled = true },
        )

        sut.onFoodConsumedSaved()

        assertTrue(onFoodSavedCalled)
        assertTrue(sut.shouldDismiss.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        searchFoodItems: SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        searchFoodExternally: SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        fetchFavouriteFoods: FetchFavouriteFoodsUseCaseProtocol = FetchFavouriteFoodsUseCaseFake(),
        refreshFavouriteFood: RefreshFavouriteFoodUseCaseProtocol = RefreshFavouriteFoodUseCaseFake(),
        fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseProtocol = FetchMyCreatedMealsUseCaseFake(),
        deleteMyCreatedMeal: DeleteMyCreatedMealUseCaseProtocol = DeleteMyCreatedMealUseCaseFake(),
        isScannerVisible: Boolean = false,
    ): AddFoodSheetViewModel = AddFoodSheetViewModel(
        searchFoodItems = searchFoodItems,
        searchFoodExternally = searchFoodExternally,
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally,
        fetchFavouriteFoods = fetchFavouriteFoods,
        refreshFavouriteFood = refreshFavouriteFood,
        fetchMyCreatedMeals = fetchMyCreatedMeals,
        deleteMyCreatedMeal = deleteMyCreatedMeal,
        isScannerVisible = isScannerVisible,
    )

    private fun makeMeal(id: String, name: String): MyCreatedMealDomain = MyCreatedMealDomain(
        id = id,
        name = name,
        ingredients = listOf(
            MyCreatedMealIngredientDomain(
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
            ),
        ),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun makeFoodItem(
        id: String = "12345",
        czName: String = "Ovesné vločky",
        kind: FoodItemKind = FoodItemKind.CATALOGUE,
    ): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = kind,
        czName = czName,
        engName = "Oats",
        weight = 80.0,
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
}
