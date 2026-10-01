package antoni.kalorie.features.moderation

import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseFake
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseProtocol
import antoni.kalorie.core.nutritionlabelrecognition.StubNutritionLabelImage
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodItemError
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseFake
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseProtocol
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModerationCatalogueEditorViewModelTest {

    @Test
    fun onSearchTapped_whenFound_prefillsFormAndHidesPreviousCheckmark() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()))
        sut.barcodeQuery.value = "12345678"

        sut.onSearchTapped()

        assertEquals("12345678", sut.formInput.value.scannedCode)
        assertFalse(sut.showCheckmark.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSearchTapped_whenNotFound_showsAlert() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = null))
        sut.barcodeQuery.value = "12345678"

        sut.onSearchTapped()

        assertEquals(R.string.addFood_error_barcodeNotFound, sut.alertItem.value?.titleRes)
    }

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
    }

    @Test
    fun onSaveTapped_forBarcodeLessItem_keepsItsUUIDAsTheItemId() = runTest {
        val uuid = "9A5E1B2C-8D3F-4A6E-9C1D-7B2A4E5F6C8D"
        val updateFoodItem = UpdateFoodItemUseCaseSpy()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem(id = uuid)),
            updateFoodItem = updateFoodItem,
        )
        sut.barcodeQuery.value = uuid
        sut.onSearchTapped()

        sut.onSaveTapped()

        assertEquals(uuid, updateFoodItem.receivedItem?.id)
    }

    @Test
    fun onSaveTapped_whenCancelledWhileTheCheckmarkShows_hidesTheCheckmark() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()))
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()

        val save = launch { sut.onSaveTapped() }
        yield()
        assertTrue(sut.showCheckmark.value)
        save.cancelAndJoin()

        assertFalse(sut.showCheckmark.value)
    }

    @Test
    fun onSaveTapped_whenItemChangedSinceLoad_showsAlertAndDoesNotShowCheckmark() = runTest {
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = UpdateFoodItemUseCaseFake(errorToThrow = UpdateFoodItemError.ChangedSinceLoad),
        )
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()

        sut.formInput.value = sut.formInput.value.copy(name = "Opravený název")
        sut.onSaveTapped()

        assertEquals(R.string.moderation_error_itemChangedSinceLoad, sut.alertItem.value?.titleRes)
        assertFalse(sut.showCheckmark.value)
    }

    @Test
    fun onNutritionLabelCaptured_onSuccess_closesCameraAndMergesWithoutTouchingFilledFields() = runTest {
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(stubbedReading = NutritionLabelReading(fat = 999.0, fiber = 1.0)),
        )
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()
        sut.isNutritionLabelCameraVisible.value = true
        val originalFat = sut.formInput.value.fat

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = null)

        assertFalse(sut.isNutritionLabelCameraVisible.value)
        assertEquals(originalFat, sut.formInput.value.fat, 0.0)
        assertEquals(1.0, sut.formInput.value.fiber ?: 0.0, 0.0)
    }

    @Test
    fun onSearchTapped_clearsThePreviousRecognizedFields() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()))
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()
        sut.recognizedFields.value = setOf(FoodItemFormField.FAT)

        sut.onSearchTapped()

        assertTrue(sut.recognizedFields.value.isEmpty())
    }

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
        recognizeNutritionLabel: RecognizeNutritionLabelUseCaseProtocol = RecognizeNutritionLabelUseCaseFake(),
        initialBarcode: String? = null,
    ): ModerationCatalogueEditorViewModel = ModerationCatalogueEditorViewModel(
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        updateFoodItem = updateFoodItem,
        recognizeNutritionLabelUseCase = recognizeNutritionLabel,
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
