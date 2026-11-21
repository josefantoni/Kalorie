package antoni.kalorie.features.moderation

import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.makeReport
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.ResolveFoodItemReportUseCaseFake
import antoni.kalorie.core.usecases.ResolveFoodItemReportUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodItemError
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseFake
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        sut.formInput.value = sut.formInput.value.copy(czName = "Opravený název")
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

        sut.formInput.value = sut.formInput.value.copy(czName = "Opravený název")
        sut.onSaveTapped()

        assertEquals(R.string.moderation_error_itemChangedSinceLoad, sut.alertItem.value?.titleRes)
        assertFalse(sut.showCheckmark.value)
    }

    @Test
    fun onNutritionLabelRecognized_onSuccess_closesCameraAndMergesWithoutTouchingFilledFields() = runTest {
        val sut = makeSUT(fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()))
        sut.barcodeQuery.value = "12345678"
        sut.onSearchTapped()
        sut.isNutritionLabelCameraVisible.value = true
        val originalFat = sut.formInput.value.fat

        sut.onNutritionLabelRecognized(NutritionLabelReading(fat = 999.0, fiber = 1.0), liveBarcode = null)

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
    fun onAppear_withATicket_prefillsFormFromItsItem() = runTest {
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            ticket = makeReport(),
        )

        sut.onAppear()

        assertEquals("12345678", sut.formInput.value.scannedCode)
        assertTrue(sut.isOpenedFromReport)
    }

    @Test
    fun onAppear_withoutATicket_doesNothing() = runTest {
        val sut = makeSUT()

        sut.onAppear()

        assertNull(sut.loadedItem.value)
        assertFalse(sut.isOpenedFromReport)
    }

    @Test
    fun onSaveAndResolveTapped_savesFirst_thenOpensTheSheetPrefilledInTheTicketsLocale() = runTest {
        val updateFoodItem = UpdateFoodItemUseCaseSpy()
        val resolve = ResolveFoodItemReportUseCaseSpy()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = updateFoodItem,
            resolveFoodItemReport = resolve,
            ticket = makeReport(locale = "cs"),
        )
        sut.onAppear()
        sut.formInput.value = sut.formInput.value.copy(czName = "Opravený název")

        sut.onSaveAndResolveTapped()

        assertEquals("Opravený název", updateFoodItem.receivedItem?.czName)
        assertEquals(TicketStatus.ACCEPTED, sut.responseDraft.value?.status)
        assertEquals("the author reads it in their language, not the maintainer's", "report-accepted-cs", sut.responseDraft.value?.text)
        assertTrue("the ticket is resolved only when the maintainer sends the response", resolve.calls.isEmpty())
        assertFalse(sut.shouldDismiss.value)
    }

    @Test
    fun onSaveAndResolveTapped_whenSaveFails_doesNotOpenTheSheetOrResolve() = runTest {
        val resolve = ResolveFoodItemReportUseCaseSpy()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = UpdateFoodItemUseCaseFake(errorToThrow = UpdateFoodItemError.ChangedSinceLoad),
            resolveFoodItemReport = resolve,
            ticket = makeReport(),
        )
        sut.onAppear()

        sut.onSaveAndResolveTapped()

        assertNull("resolving a report whose correction was not saved would close it with the item still wrong", sut.responseDraft.value)
        assertTrue(resolve.calls.isEmpty())
        assertEquals(R.string.moderation_error_itemChangedSinceLoad, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onRejectTapped_opensAnEmptySheetWithoutSavingTheForm() = runTest {
        val updateFoodItem = UpdateFoodItemUseCaseSpy()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = updateFoodItem,
            ticket = makeReport(),
        )
        sut.onAppear()
        sut.formInput.value = sut.formInput.value.copy(czName = "Rozpracovaná úprava")

        sut.onRejectTapped()

        assertNull("rejecting a report judges it wrong, so the catalogue entry must stay untouched", updateFoodItem.receivedItem)
        assertEquals(TicketStatus.REJECTED, sut.responseDraft.value?.status)
        assertEquals("a rejection must be explained to the author", false, sut.responseDraft.value?.isSendable)
    }

    @Test
    fun onResponseSendTapped_resolvesTheTicketWithTheEditedResponse_andDismisses() = runTest {
        val resolve = ResolveFoodItemReportUseCaseSpy()
        var resolved = 0
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            resolveFoodItemReport = resolve,
            ticket = makeReport(id = "report-7"),
            onResolved = { resolved += 1 },
        )
        sut.onAppear()
        sut.onSaveAndResolveTapped()
        sut.onResponseTextChanged("Fixed it")

        sut.onResponseSendTapped()

        assertEquals(listOf(ResolveCall("report-7", TicketStatus.ACCEPTED, "Fixed it")), resolve.calls)
        assertEquals("the parent list and the bell must learn the ticket is gone from the queue", 1, resolved)
        assertNull(sut.responseDraft.value)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onResponseSendTapped_whenRejecting_resolvesAsRejected() = runTest {
        val resolve = ResolveFoodItemReportUseCaseSpy()
        val sut = makeSUT(resolveFoodItemReport = resolve, ticket = makeReport(id = "report-8"))
        sut.onRejectTapped()
        sut.onResponseTextChanged("Not reproducible")

        sut.onResponseSendTapped()

        assertEquals(listOf(ResolveCall("report-8", TicketStatus.REJECTED, "Not reproducible")), resolve.calls)
    }

    @Test
    fun onResponseSendTapped_whenTheTicketWriteFails_keepsTheItemSavedAndTheSheetOpenForARetry() = runTest {
        val resolve = ResolveFoodItemReportUseCaseSpy()
        resolve.error = java.io.IOException("offline")
        var resolved = 0
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            resolveFoodItemReport = resolve,
            ticket = makeReport(),
            onResolved = { resolved += 1 },
        )
        sut.onAppear()
        sut.onSaveAndResolveTapped()

        sut.onResponseSendTapped()

        assertNotNull("the maintainer must not have to retype the response", sut.responseDraft.value)
        assertNotNull(sut.responseDraft.value?.errorTitleRes)
        assertEquals(0, resolved)
        assertFalse(sut.shouldDismiss.value)

        resolve.error = null
        sut.onResponseSendTapped()

        assertEquals(1, resolve.calls.size)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onResponseSendTapped_withEmptyText_doesNotWrite() = runTest {
        val resolve = ResolveFoodItemReportUseCaseSpy()
        val sut = makeSUT(resolveFoodItemReport = resolve, ticket = makeReport())
        sut.onRejectTapped()

        sut.onResponseSendTapped()

        assertTrue(resolve.calls.isEmpty())
        assertNotNull(sut.responseDraft.value)
    }

    @Test
    fun onResponseSendTapped_whileASendIsRunning_isIgnored() = runTest {
        val resolve = ResolveFoodItemReportUseCaseSpy()
        resolve.gate = CompletableDeferred()
        val sut = makeSUT(resolveFoodItemReport = resolve, ticket = makeReport())
        sut.onRejectTapped()
        sut.onResponseTextChanged("No")

        val first = launch { sut.onResponseSendTapped() }
        yield()
        sut.onResponseSendTapped()
        resolve.gate?.complete(Unit)
        first.join()

        assertEquals("a double tap must not write the resolution twice", 1, resolve.calls.size)
    }

    @Test
    fun onSaveAndResolveTapped_whileASaveIsRunning_isIgnored() = runTest {
        val updateFoodItem = SuspendingUpdateFoodItemUseCase()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = updateFoodItem,
            ticket = makeReport(),
        )
        sut.onAppear()

        val first = launch { sut.onSaveAndResolveTapped() }
        yield()
        sut.onSaveAndResolveTapped()
        sut.onRejectTapped()
        updateFoodItem.gate.complete(Unit)
        first.join()

        assertEquals("a double tap must not save twice", 1, updateFoodItem.callCount)
        assertEquals(TicketStatus.ACCEPTED, sut.responseDraft.value?.status)
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        updateFoodItem: UpdateFoodItemUseCaseProtocol = UpdateFoodItemUseCaseFake(),
        resolveFoodItemReport: ResolveFoodItemReportUseCaseProtocol = ResolveFoodItemReportUseCaseFake(),
        ticket: FoodItemReportDomain? = null,
        onResolved: () -> Unit = {},
        applicationScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): ModerationCatalogueEditorViewModel = ModerationCatalogueEditorViewModel(
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        updateFoodItem = updateFoodItem,
        resolveFoodItemReport = resolveFoodItemReport,
        responseTextProvider = ModerationResponseTextProviderFake(),
        ticket = ticket,
        applicationScope = applicationScope,
        onResolved = onResolved,
    )

    private fun makeItem(id: String = "12345678", czName: String = "Tvaroh", date: Instant = Instant.now()): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = czName,
        skName = czName,
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

    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Boolean): FoodItemDomain {
        receivedItem = item
        return item
    }
}

private class SuspendingUpdateFoodItemUseCase : UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    var callCount = 0
    val gate = CompletableDeferred<Unit>()

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Boolean): FoodItemDomain {
        callCount += 1
        gate.await()
        return item
    }
}

private data class ResolveCall(val ticketId: String, val status: TicketStatus, val response: String)

private class ResolveFoodItemReportUseCaseSpy : ResolveFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    var error: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<ResolveCall>()

    // MARK: - Functions

    override suspend fun invoke(ticketId: String, status: TicketStatus, response: String) {
        error?.let { throw it }
        calls.add(ResolveCall(ticketId, status, response))
        gate?.await()
    }
}
