package antoni.kalorie.features.moderation

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodItemError
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseFake
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseProtocol
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationCatalogueEditorViewModelTest {

    // MARK: - onSearchTapped

    @Test
    fun onSearchTapped_whenFound_prefillsFormAndClearsPreviousSavedFlag() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()))
        sut.barcodeQuery.value = "12345678"

        sut.onSearchTapped()

        assertEquals("12345678", sut.formInput.value.scannedCode)
        assertFalse(sut.didSave.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSearchTapped_whenNotFound_showsAlert() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = null))
        sut.barcodeQuery.value = "12345678"

        sut.onSearchTapped()

        assertEquals(R.string.addFood_error_barcodeNotFound, sut.alertItem.value?.titleRes)
    }

    // MARK: - onSaveTapped

    @Test
    fun onSaveTapped_preservesTheOriginalItemsDate() = runTest {
        val originalDate = Instant.ofEpochSecond(1_700_000_000)
        val updateFoodItem = UpdateFoodItemUseCaseSpy()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem(date = originalDate)),
            updateFoodItem = updateFoodItem,
        )
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()

        sut.formInput.value = sut.formInput.value.copy(name = "Opravený název")
        sut.onSaveTapped()

        assertEquals(originalDate, updateFoodItem.receivedItem?.date)
        assertTrue(sut.didSave.value)
    }

    @Test
    fun onSaveTapped_whenItemChangedSinceLoad_showsAlertAndDoesNotMarkSaved() = runTest {
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = UpdateFoodItemUseCaseFake(errorToThrow = UpdateFoodItemError.ChangedSinceLoad),
        )
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()

        sut.formInput.value = sut.formInput.value.copy(name = "Opravený název")
        sut.onSaveTapped()

        assertEquals(R.string.moderation_error_itemChangedSinceLoad, sut.alertItem.value?.titleRes)
        assertFalse(sut.didSave.value)
    }

    // MARK: - onAppear (initialBarcode)

    @Test
    fun onAppear_withInitialBarcode_prefillsFormFromIt() = runTest {
        val item = makeItem()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item),
            initialBarcode = "12345678",
        )

        sut.onAppear()

        assertEquals(item, sut.loadedItem.value)
    }

    @Test
    fun onAppear_withoutInitialBarcode_doesNothing() = runTest {
        val sut = makeSUT()

        sut.onAppear()

        assertNull(sut.loadedItem.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        updateFoodItem: UpdateFoodItemUseCaseProtocol = UpdateFoodItemUseCaseFake(),
        initialBarcode: String? = null,
    ): ModerationCatalogueEditorViewModel = ModerationCatalogueEditorViewModel(
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        updateFoodItem = updateFoodItem,
        initialBarcode = initialBarcode,
    )

    private fun makeItem(id: String = "12345678", czName: String = "Tvaroh", date: Instant = Instant.now()): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = czName,
        engName = "Cottage cheese",
        weight = 200.0,
        date = date,
        energyKJ = 335.0,
        caloriesPerHundredGrams = 80.0,
        fat = 0.5,
        fatSaturated = 0.3,
        fatUnsaturatedFattyAcids = 0.2,
        carbohydrate = 4.0,
        carbohydratePureSugar = 3.0,
        fiber = 0.0,
        protein = 13.0,
        salt = 0.1,
    )
}

private class UpdateFoodItemUseCaseSpy : UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    var receivedItem: FoodItemDomain? = null

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain) {
        receivedItem = item
    }
}
