package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseFake
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseProtocol
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseFake
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseFake
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFoodSheetViewModelTest {

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
            fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(),
            refreshFavouriteFood = RefreshFavouriteFoodUseCaseFake(),
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
        fetchFavouriteFoods: FetchFavouriteFoodsUseCaseProtocol = FetchFavouriteFoodsUseCaseFake(),
        refreshFavouriteFood: RefreshFavouriteFoodUseCaseProtocol = RefreshFavouriteFoodUseCaseFake(),
    ): AddFoodSheetViewModel = AddFoodSheetViewModel(
        searchFoodItems = searchFoodItems,
        searchFoodExternally = searchFoodExternally,
        fetchFavouriteFoods = fetchFavouriteFoods,
        refreshFavouriteFood = refreshFavouriteFood,
    )

    private fun makeFoodItem(id: String = "12345", czName: String = "Ovesné vločky"): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
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
