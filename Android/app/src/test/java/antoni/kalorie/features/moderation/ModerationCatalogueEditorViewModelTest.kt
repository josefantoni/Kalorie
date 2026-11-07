package antoni.kalorie.features.moderation

import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.usecases.DeleteFoodItemReportUseCaseFake
import antoni.kalorie.core.usecases.DeleteFoodItemReportUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodItemError
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseFake
import antoni.kalorie.core.usecases.UpdateFoodItemUseCaseProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    @Test
    fun onSaveAndResolveTapped_savesThenDeletesEveryReportAndDismisses() = runTest {
        val updateFoodItem = UpdateFoodItemUseCaseSpy()
        val deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        val changedReports = mutableListOf<List<String>>()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = updateFoodItem,
            deleteFoodItemReport = deleteFoodItemReport,
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1"), makeReport("user-2")),
            onReportsChanged = { changedReports += it.map { report -> report.reportedBy } },
        )
        sut.onAppear()

        sut.formInput.value = sut.formInput.value.copy(name = "Opravený název")
        sut.onSaveAndResolveTapped()

        assertEquals("Opravený název", updateFoodItem.receivedItem?.czName)
        assertEquals(listOf("user-1", "user-2"), deleteFoodItemReport.deletedReportedBy.sorted())
        assertEquals(listOf(emptyList<String>()), changedReports)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onSaveAndResolveTapped_whenSaveFails_keepsTheReportsOpen() = runTest {
        val deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        val changedReports = mutableListOf<List<String>>()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = UpdateFoodItemUseCaseFake(errorToThrow = UpdateFoodItemError.ChangedSinceLoad),
            deleteFoodItemReport = deleteFoodItemReport,
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1")),
            onReportsChanged = { changedReports += it.map { report -> report.reportedBy } },
        )
        sut.onAppear()

        sut.onSaveAndResolveTapped()

        assertTrue(deleteFoodItemReport.deletedReportedBy.isEmpty())
        assertTrue(changedReports.isEmpty())
        assertFalse(sut.shouldDismiss.value)
        assertEquals(R.string.moderation_error_itemChangedSinceLoad, sut.alertItem.value?.titleRes)
    }

    @Test
    fun onRejectTapped_deletesEveryReportWithoutSavingTheForm() = runTest {
        val updateFoodItem = UpdateFoodItemUseCaseSpy()
        val deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = updateFoodItem,
            deleteFoodItemReport = deleteFoodItemReport,
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1"), makeReport("user-2")),
        )
        sut.onAppear()

        sut.formInput.value = sut.formInput.value.copy(name = "Rozpracovaná úprava")
        sut.onRejectTapped()

        assertNull(updateFoodItem.receivedItem)
        assertEquals(listOf("user-1", "user-2"), deleteFoodItemReport.deletedReportedBy.sorted())
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onRejectTapped_whenSomeDeletesFail_keepsOnlyTheFailedReportsAndDoesNotDismiss() = runTest {
        val changedReports = mutableListOf<List<String>>()
        val sut = makeSUT(
            deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy(failingReportedBy = setOf("user-2")),
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1"), makeReport("user-2")),
            onReportsChanged = { changedReports += it.map { report -> report.reportedBy } },
        )

        sut.onRejectTapped()

        assertEquals(listOf("user-2"), sut.reports.value.map { it.reportedBy })
        assertNotNull(sut.alertItem.value)
        assertEquals(listOf(listOf("user-2")), changedReports)
        assertFalse(sut.shouldDismiss.value)
    }

    @Test
    fun actions_whileAnotherActionIsRunning_areIgnored() = runTest {
        val updateFoodItem = SuspendingUpdateFoodItemUseCase()
        val deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        val changedReports = mutableListOf<List<String>>()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = makeItem()),
            updateFoodItem = updateFoodItem,
            deleteFoodItemReport = deleteFoodItemReport,
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1")),
            onReportsChanged = { changedReports += it.map { report -> report.reportedBy } },
        )
        sut.onAppear()

        val first = launch { sut.onSaveAndResolveTapped() }
        yield()
        sut.onSaveAndResolveTapped()
        sut.onRejectTapped()
        updateFoodItem.gate.complete(Unit)
        first.join()

        assertEquals(1, updateFoodItem.callCount)
        assertEquals(listOf("user-1"), deleteFoodItemReport.deletedReportedBy)
        assertEquals(listOf(emptyList<String>()), changedReports)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSaveAndResolveTapped_retryAfterPartialDeleteFailure_savesAgainWithoutChangedSinceLoad() = runTest {
        val item = makeItem()
        val updateFoodItem = StoringUpdateFoodItemUseCase(stored = item, uploadedUrl = "https://example.com/uploaded.jpg")
        val deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy(failingReportedBy = setOf("user-2"))
        val changedReports = mutableListOf<List<String>>()
        val sut = makeSUT(
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(stubbedItem = item),
            updateFoodItem = updateFoodItem,
            deleteFoodItemReport = deleteFoodItemReport,
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1"), makeReport("user-2")),
            onReportsChanged = { changedReports += it.map { report -> report.reportedBy } },
        )
        sut.onAppear()
        sut.formInput.value = sut.formInput.value.copy(photo = FoodItemFormPhoto.Local(byteArrayOf(1, 2)))

        sut.onSaveAndResolveTapped()
        deleteFoodItemReport.failingReportedBy = emptySet()
        sut.alertItem.value = null
        sut.onSaveAndResolveTapped()

        assertNull(sut.alertItem.value)
        assertEquals(listOf("user-1", "user-2"), deleteFoodItemReport.deletedReportedBy.sorted())
        assertEquals(listOf(listOf("user-2"), emptyList()), changedReports)
        assertTrue(sut.shouldDismiss.value)
    }

    @Test
    fun onRejectTapped_whenTheCallerIsCancelledMidway_stillDeletesEveryReportAndNotifies() = runTest {
        val changedReports = mutableListOf<List<String>>()
        var caller: Job? = null
        val deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy(onDeleted = { caller?.cancel() })
        val sut = makeSUT(
            deleteFoodItemReport = deleteFoodItemReport,
            initialBarcode = "12345678",
            reports = listOf(makeReport("user-1"), makeReport("user-2"), makeReport("user-3")),
            onReportsChanged = { changedReports += it.map { report -> report.reportedBy } },
        )

        caller = launch { sut.onRejectTapped() }
        caller.join()

        assertEquals(listOf("user-1", "user-2", "user-3"), deleteFoodItemReport.deletedReportedBy)
        assertEquals(listOf(emptyList<String>()), changedReports)
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        updateFoodItem: UpdateFoodItemUseCaseProtocol = UpdateFoodItemUseCaseFake(),
        deleteFoodItemReport: DeleteFoodItemReportUseCaseProtocol = DeleteFoodItemReportUseCaseFake(),
        initialBarcode: String? = null,
        reports: List<FoodItemReportDomain> = emptyList(),
        onReportsChanged: (List<FoodItemReportDomain>) -> Unit = {},
        applicationScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): ModerationCatalogueEditorViewModel = ModerationCatalogueEditorViewModel(
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        updateFoodItem = updateFoodItem,
        deleteFoodItemReport = deleteFoodItemReport,
        initialBarcode = initialBarcode,
        reports = reports,
        applicationScope = applicationScope,
        onReportsChanged = onReportsChanged,
    )

    private fun makeReport(reportedBy: String): FoodItemReportDomain = FoodItemReportDomain(barcode = "12345678", reportedBy = reportedBy, reason = "wrong data", reportedAt = Instant.now())

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

private class StoringUpdateFoodItemUseCase(
    private var stored: FoodItemDomain,
    private val uploadedUrl: String,
) : UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    var callCount = 0

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Boolean): FoodItemDomain {
        callCount += 1
        if (previouslyLoaded != stored) throw UpdateFoodItemError.ChangedSinceLoad
        stored = if (photo is FoodItemFormPhoto.Local) item.withPhotoUrl(uploadedUrl) else item
        return stored
    }
}

private class DeleteFoodItemReportUseCaseSpy(
    var failingReportedBy: Set<String> = emptySet(),
    var onDeleted: () -> Unit = {},
) : DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    val deletedReportedBy = mutableListOf<String>()

    // MARK: - Functions

    override suspend fun invoke(barcode: String, reportedBy: String) {
        if (reportedBy in failingReportedBy) throw RuntimeException("delete failed")
        deletedReportedBy += reportedBy
        onDeleted()
        yield()
    }
}
