package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
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

    // MARK: - onFoodConsumedSaved

    @Test
    fun onFoodConsumedSaved_notifiesTheDashboardAndRequestsDismissal() {
        var onFoodSavedCalled = false
        val sut = AddFoodSheetViewModel(
            searchFoodItems = SearchFoodItemsUseCaseFake(),
            searchFoodExternally = SearchFoodExternallyUseCaseFake(),
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
    ): AddFoodSheetViewModel = AddFoodSheetViewModel(searchFoodItems = searchFoodItems, searchFoodExternally = searchFoodExternally)

    private fun makeFoodItem(id: String = "12345"): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = "Ovesné vločky",
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
