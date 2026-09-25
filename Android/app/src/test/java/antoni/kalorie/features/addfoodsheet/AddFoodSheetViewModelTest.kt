package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.nutritionlabelrecognition.StubNutritionLabelImage
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelRecognitionError
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseFake
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseProtocol
import antoni.kalorie.core.utils.CameraAccess
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionError
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.FoodNutritionValues
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCaseFake
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteMySubmissionUseCaseFake
import antoni.kalorie.core.usecases.DeleteMySubmissionUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCaseFake
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMySubmissionsUseCaseFake
import antoni.kalorie.core.usecases.FetchMySubmissionsUseCaseProtocol
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
import antoni.kalorie.core.usecases.SubmitFoodItemUseCaseFake
import antoni.kalorie.core.usecases.SubmitFoodItemUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMySubmissionUseCaseFake
import antoni.kalorie.core.usecases.UpdateMySubmissionUseCaseProtocol
import antoni.kalorie.R
import java.time.Instant
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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

    // MARK: - own submissions in displayedResults

    @Test
    fun displayedResults_includesMatchingOwnSubmission() = runTest {
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(makeSubmission(barcode = "sub-item", status = FoodItemSubmissionStatus.REJECTED))),
        )
        sut.onAppear()
        sut.searchText.value = "ov"

        assertTrue(sut.displayedResults.map { it.id }.contains("sub-item"))
    }

    @Test
    fun submissionStatus_forMatchingSubmission_returnsItsStatus() = runTest {
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(makeSubmission(barcode = "sub-item", status = FoodItemSubmissionStatus.REJECTED))),
        )
        sut.onAppear()

        assertEquals(FoodItemSubmissionStatus.REJECTED, sut.submissionStatus(makeFoodItem(id = "sub-item")))
        assertNull(sut.submissionStatus(makeFoodItem(id = "other-item")))
    }

    // MARK: - onSelectRejectedSubmission

    @Test
    fun onSelectRejectedSubmission_prefillsFormAndShowsRejectionReason() = runTest {
        val submission = makeSubmission(id = "sub-1", barcode = "87654321", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val sut = makeSUT(fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)))
        sut.onAppear()

        sut.onSelectRejectedSubmission(makeFoodItem(id = "87654321", czName = "Ovar"))

        assertEquals(AddFoodSheetMode.NEW_ITEM, sut.mode.value)
        assertEquals("87654321", sut.formInput.value.scannedCode)
        assertEquals("Ovar", sut.formInput.value.name)
        assertEquals("Wrong calories", sut.rejectionReasonBeingEdited.value)
        assertTrue("the barcode must be locked while resubmitting, or approving it can orphan entries logged under the old barcode", sut.isEditingSubmission)
        assertTrue("editing a rejected submission must push the review screen directly, skipping the prompt", sut.isReviewPushed.value)
    }

    // MARK: - onModeSelected (new item)

    @Test
    fun onModeSelected_afterClosingRejectedSubmissionEdit_reopensAsFreshFormAndSubmitsNewItem() = runTest {
        val submission = makeSubmission(id = "sub-1", barcode = "sub-item", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val submitFoodItem = SubmitFoodItemUseCaseSpy()
        val updateMySubmission = UpdateMySubmissionUseCaseSpy()
        val sut = makeSUT(
            submitFoodItem = submitFoodItem,
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)),
            updateMySubmission = updateMySubmission,
        )
        sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id = "sub-item", czName = "Ovar"))
        assertEquals(AddFoodSheetMode.NEW_ITEM, sut.mode.value)

        sut.onModeSelected(AddFoodSheetMode.SEARCH)
        assertEquals("closing the form must not leave it silently pointed at the old submission", AddFoodSheetMode.SEARCH, sut.mode.value)
        assertFalse("leaving the new item tab must close a review pushed for the old submission", sut.isReviewPushed.value)

        sut.onModeSelected(AddFoodSheetMode.NEW_ITEM)
        assertEquals(AddFoodSheetMode.NEW_ITEM, sut.mode.value)
        assertNull(sut.rejectionReasonBeingEdited.value)
        assertEquals("", sut.formInput.value.scannedCode)
        assertFalse("a freshly reopened form must allow a barcode again", sut.isEditingSubmission)
        assertFalse("reopening the tab must show the prompt again, not the old review screen", sut.isReviewPushed.value)

        sut.formInput.value = sut.formInput.value.copy(scannedCode = "99999999", name = "Nová položka", weightOfProduct = 100.0, caloriesPerHundredGrams = 50.0)
        sut.onCreateFoodItem()

        assertEquals("a freshly reopened form must submit a new submission", "99999999", submitFoodItem.receivedItem?.id)
        assertNull("it must not silently overwrite the previously edited submission", updateMySubmission.receivedId)
    }

    @Test
    fun onModeSelected_withAlreadyActiveMode_keepsHalfTypedForm() {
        val sut = makeSUT()
        sut.onModeSelected(AddFoodSheetMode.NEW_ITEM)
        sut.formInput.value = sut.formInput.value.copy(name = "Ovar")

        sut.onModeSelected(AddFoodSheetMode.NEW_ITEM)

        assertEquals("re-tapping the active segment must not wipe a half-typed form", "Ovar", sut.formInput.value.name)
    }

    // MARK: - onSelectSubmission

    @Test
    fun onSelectSubmission_forRejectedSubmission_opensThatExactSubmissionDespiteDuplicateBarcode() = runTest {
        val rejected = makeSubmission(id = "sub-old", barcode = "shared-barcode", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val pending = makeSubmission(id = "sub-new", barcode = "shared-barcode", status = FoodItemSubmissionStatus.PENDING)
        val sut = makeSUT(fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(pending, rejected)))
        sut.onAppear()

        sut.onSelectSubmission(rejected)

        assertEquals(AddFoodSheetMode.NEW_ITEM, sut.mode.value)
        assertEquals("Wrong calories", sut.rejectionReasonBeingEdited.value)
    }

    @Test
    fun onSelectSubmission_forPendingSubmission_navigatesToFoodItemDespiteDuplicateBarcode() = runTest {
        val rejected = makeSubmission(id = "sub-old", barcode = "shared-barcode", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val pending = makeSubmission(id = "sub-new", barcode = "shared-barcode", status = FoodItemSubmissionStatus.PENDING)
        val sut = makeSUT(fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(pending, rejected)))
        sut.onAppear()

        sut.onSelectSubmission(pending)

        assertTrue(sut.isPushedToQuantityView.value)
        assertEquals(AddFoodSheetMode.SEARCH, sut.mode.value)
    }

    // MARK: - onCreateFoodItem

    @Test
    fun onCreateFoodItem_withNoEditingSubmission_submitsNewFoodItem() = runTest {
        val submitFoodItem = SubmitFoodItemUseCaseSpy()
        val sut = makeSUT(submitFoodItem = submitFoodItem)
        fillValidForm(sut, scannedCode = "12345678")

        sut.onCreateFoodItem()

        assertEquals("12345678", submitFoodItem.receivedItem?.id)
        assertTrue(sut.isSubmissionConfirmationVisible.value)
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onCreateFoodItem_whenResubmittingRejectedSubmission_callsUpdateMySubmission() = runTest {
        val submission = makeSubmission(id = "sub-1", barcode = "87654321", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val updateMySubmission = UpdateMySubmissionUseCaseSpy()
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)),
            updateMySubmission = updateMySubmission,
        )
        sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id = "87654321", czName = "Ovar"))
        sut.formInput.value = sut.formInput.value.copy(weightOfProduct = 200.0, caloriesPerHundredGrams = 80.0)

        sut.onCreateFoodItem()

        assertEquals("sub-1", updateMySubmission.receivedId)
        assertTrue(sut.isSubmissionConfirmationVisible.value)
        assertNull("resolving the resubmission must clear the rejection banner", sut.rejectionReasonBeingEdited.value)
    }

    @Test
    fun onCreateFoodItem_whenBarcodeAlreadyExists_showsAlertAndKeepsFormOpen() = runTest {
        val sut = makeSUT(submitFoodItem = SubmitFoodItemUseCaseFake(errorToThrow = FoodItemSubmissionError.ItemAlreadyExists))
        fillValidForm(sut, scannedCode = "12345678")

        sut.onCreateFoodItem()

        assertEquals(R.string.addFood_error_itemAlreadyExists, sut.alertItem.value?.titleRes)
        assertFalse(sut.isSubmissionConfirmationVisible.value)
    }

    @Test
    fun onCreateFoodItem_withEmptyBarcode_showsConfirmationAndWritesNothingUntilConfirmed() = runTest {
        val submitFoodItem = SubmitFoodItemUseCaseSpy()
        val sut = makeSUT(submitFoodItem = submitFoodItem)
        fillValidForm(sut, scannedCode = "")

        sut.onCreateFoodItem()

        assertTrue("a food with no barcode must be confirmed explicitly before it is submitted", sut.isMissingBarcodeConfirmationVisible.value)
        assertNull("nothing may be written before the user confirms", submitFoodItem.receivedItem)
        assertFalse(sut.isSubmissionConfirmationVisible.value)
    }

    @Test
    fun onMissingBarcodeConfirmed_submitsTheBarcodelessItemAndHidesTheConfirmation() = runTest {
        val submitFoodItem = SubmitFoodItemUseCaseSpy()
        val sut = makeSUT(submitFoodItem = submitFoodItem)
        fillValidForm(sut, scannedCode = "")
        sut.onCreateFoodItem()

        sut.onMissingBarcodeConfirmed()

        assertFalse(sut.isMissingBarcodeConfirmationVisible.value)
        assertEquals("the writer, not the form, assigns the submission's identity when there is no barcode", "", submitFoodItem.receivedItem?.id)
        assertTrue(sut.isSubmissionConfirmationVisible.value)
    }

    // MARK: - onAddManuallyTapped

    @Test
    fun onAddManuallyTapped_resetsFormAndPushesReview() {
        val sut = makeSUT()

        sut.onAddManuallyTapped()

        assertTrue(sut.isReviewPushed.value)
        assertEquals("", sut.formInput.value.scannedCode)
        assertEquals("", sut.formInput.value.name)
    }

    // MARK: - barcode rescan

    @Test
    fun onBarcodeRescanned_copiesTheCodeIntoTheFormAndClosesTheScanner() {
        val sut = makeSUT()
        sut.isBarcodeRescanVisible.value = true
        sut.rescannedBarcode.value = "8594004428464"

        sut.onBarcodeRescanned()

        assertEquals("8594004428464", sut.formInput.value.scannedCode)
        assertEquals("", sut.rescannedBarcode.value)
        assertFalse(sut.isBarcodeRescanVisible.value)
    }

    // MARK: - onSubmissionConfirmationDismissed

    @Test
    fun onSubmissionConfirmationDismissed_dismissesSheet() {
        val sut = makeSUT()
        sut.isSubmissionConfirmationVisible.value = true

        sut.onSubmissionConfirmationDismissed()

        assertFalse(sut.isSubmissionConfirmationVisible.value)
        assertTrue(sut.shouldDismiss.value)
    }

    // MARK: - onDeleteSubmissionRequested / onDeleteSubmissionConfirmed

    @Test
    fun onDeleteSubmissionRequested_withoutConfirming_deletesNothing() = runTest {
        val deleteMySubmission = DeleteMySubmissionUseCaseSpy()
        val submission = makeSubmission(id = "sub-1", barcode = "sub-item", status = FoodItemSubmissionStatus.PENDING)
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)),
            deleteMySubmission = deleteMySubmission,
        )
        sut.onAppear()

        sut.onDeleteSubmissionRequested(submission)

        assertTrue(sut.isSubmissionDeleteConfirmationVisible.value)
        assertNull(deleteMySubmission.receivedId)
        assertEquals(listOf("sub-1"), sut.mySubmissions.value.map { it.id })
    }

    @Test
    fun onDeleteSubmissionConfirmed_onSuccess_removesEntryAndClearsSubmissionStatus() = runTest {
        val deleteMySubmission = DeleteMySubmissionUseCaseSpy()
        val submission = makeSubmission(id = "sub-1", barcode = "sub-item", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)),
            deleteMySubmission = deleteMySubmission,
        )
        sut.onAppear()
        sut.onDeleteSubmissionRequested(submission)

        sut.onDeleteSubmissionConfirmed()

        assertEquals("sub-1", deleteMySubmission.receivedId)
        assertTrue(sut.mySubmissions.value.isEmpty())
        assertNull(sut.submissionStatus(makeFoodItem(id = "sub-item")))
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onDeleteSubmissionConfirmed_onFailure_restoresEntryAndShowsAlert() = runTest {
        val deleteMySubmission = DeleteMySubmissionUseCaseSpy(errorToThrow = RuntimeException("unknown"))
        val submission = makeSubmission(id = "sub-1", barcode = "sub-item", status = FoodItemSubmissionStatus.PENDING)
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)),
            deleteMySubmission = deleteMySubmission,
        )
        sut.onAppear()
        sut.onDeleteSubmissionRequested(submission)

        sut.onDeleteSubmissionConfirmed()

        assertEquals("sub-1", deleteMySubmission.receivedId)
        assertEquals("a failed withdrawal must restore the entry, not silently drop it", listOf("sub-1"), sut.mySubmissions.value.map { it.id })
        assertEquals(R.string.addFood_error_withdrawSubmissionFailed, sut.alertItem.value?.titleRes)
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

    // MARK: - onNutritionLabelPromptTapped

    @Test
    fun onNutritionLabelPromptTapped_opensCamera() {
        val sut = makeSUT()

        sut.onNutritionLabelPromptTapped()

        assertTrue(sut.isNutritionLabelCameraVisible.value)
        assertEquals(CameraAccess.AUTHORIZED, sut.cameraAccess.value)
    }

    @Test
    fun onNutritionLabelCameraAccessDenied_recordsDeniedAndKeepsCameraClosed() {
        val sut = makeSUT()

        sut.onNutritionLabelCameraAccessDenied()

        assertEquals(CameraAccess.DENIED, sut.cameraAccess.value)
        assertFalse(sut.isNutritionLabelCameraVisible.value)
    }

    @Test
    fun onNutritionLabelPromptTapped_afterEditingRejectedSubmission_startsAFreshUneditedItem() = runTest {
        val submission = makeSubmission(id = "sub-1", barcode = "sub-item", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val sut = makeSUT(fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)))
        sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id = "sub-item", czName = "Ovar"))
        assertTrue(sut.isEditingSubmission)

        sut.onNutritionLabelPromptTapped()

        assertFalse(sut.isEditingSubmission)
        assertNull(sut.rejectionReasonBeingEdited.value)
        assertEquals("", sut.formInput.value.scannedCode)
    }

    // MARK: - onNutritionLabelCaptured / onNutritionLabelCameraDismissed

    @Test
    fun onNutritionLabelCaptured_onSuccess_closesCameraAndPushesOnlyAfterDismissed() = runTest {
        val reading = NutritionLabelReading(caloriesPerHundredGrams = 80.0, fat = 12.0, carbohydrate = 55.0, protein = 10.0)
        val sut = makeSUT(recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(stubbedReading = reading))
        sut.isNutritionLabelCameraVisible.value = true

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = null)

        assertFalse(sut.isNutritionLabelCameraVisible.value)
        assertFalse(sut.isReviewPushed.value)

        sut.onNutritionLabelCameraDismissed()

        assertTrue(sut.isReviewPushed.value)
    }

    @Test
    fun onNutritionLabelCameraDismissed_afterAFailedCapture_doesNotPush() = runTest {
        val sut = makeSUT(
            recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(errorToThrow = NutritionLabelRecognitionError.NothingRecognized),
        )
        sut.isNutritionLabelCameraVisible.value = true

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = null)
        sut.onNutritionLabelCameraDismissed()

        assertFalse(sut.isReviewPushed.value)
    }

    @Test
    fun onNutritionLabelCaptured_nothingRecognized_keepsCameraOpenSetsHintAndLeavesFormUntouched() = runTest {
        val sut = makeSUT(
            recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(errorToThrow = NutritionLabelRecognitionError.NothingRecognized),
        )
        sut.isNutritionLabelCameraVisible.value = true

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = null)

        assertTrue(sut.isNutritionLabelCameraVisible.value)
        assertEquals(R.string.addFood_nutritionLabel_nothingRecognized, sut.nutritionLabelCameraHintRes.value)
        assertNull(sut.alertItem.value)
        assertEquals("", sut.formInput.value.name)
    }

    @Test
    fun onNutritionLabelCaptured_readingWithOnlyABarcode_isTreatedAsNothingRecognized() = runTest {
        val sut = makeSUT(recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(stubbedReading = NutritionLabelReading(scannedCode = "12345678")))
        sut.isNutritionLabelCameraVisible.value = true

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = null)

        assertTrue(sut.isNutritionLabelCameraVisible.value)
        assertEquals(R.string.addFood_nutritionLabel_nothingRecognized, sut.nutritionLabelCameraHintRes.value)
    }

    @Test
    fun onNutritionLabelCaptured_liveBarcodeFillsScannedCodeWhenStillHadNone() = runTest {
        val reading = NutritionLabelReading(caloriesPerHundredGrams = 80.0, fat = 12.0, carbohydrate = 55.0, protein = 10.0)
        val sut = makeSUT(recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(stubbedReading = reading))

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = "8594004428464")

        assertEquals("8594004428464", sut.formInput.value.scannedCode)
    }

    @Test
    fun onNutritionLabelCaptured_liveBarcodeDoesNotOverwriteALockedBarcode() = runTest {
        val submission = makeSubmission(id = "sub-1", barcode = "87654321", status = FoodItemSubmissionStatus.REJECTED, rejectReason = "Wrong calories")
        val reading = NutritionLabelReading(caloriesPerHundredGrams = 80.0, fat = 12.0, carbohydrate = 55.0, protein = 10.0)
        val sut = makeSUT(
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(stubbedSubmissions = listOf(submission)),
            recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(stubbedReading = reading),
        )
        sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id = "87654321", czName = "Ovar"))

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = "8594004428464")

        assertEquals("87654321", sut.formInput.value.scannedCode)
    }

    @Test
    fun onNutritionLabelCaptured_marksTheFilledFieldsAsRecognizedUntilTheyAreEdited() = runTest {
        val reading = NutritionLabelReading(caloriesPerHundredGrams = 80.0, fat = 12.0, carbohydrate = 55.0, protein = 10.0)
        val sut = makeSUT(recognizeNutritionLabel = RecognizeNutritionLabelUseCaseFake(stubbedReading = reading))

        sut.onNutritionLabelCaptured(StubNutritionLabelImage, liveBarcode = null)

        assertTrue(FoodItemFormField.FAT in sut.recognizedFields.value)
        sut.onFormFieldEdited(FoodItemFormField.FAT)
        assertFalse(FoodItemFormField.FAT in sut.recognizedFields.value)
    }

    @Test
    fun onScenePhaseActive_whenCameraPermissionWasRevoked_closesTheNutritionLabelCameraAndRecordsDenied() {
        val sut = makeSUT()
        sut.onNutritionLabelPromptTapped()

        sut.onScenePhaseActive(isCameraAvailable = false)

        assertFalse(sut.isNutritionLabelCameraVisible.value)
        assertEquals(CameraAccess.DENIED, sut.cameraAccess.value)
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
        assertNull(sut.alertItem.value)
    }

    @Test
    fun onSearchTextChanged_whenTheQueryChangesDuringTheDebounce_searchesOnlyTheLatestQuery() = runTest {
        val searchFoodItems = SearchFoodItemsUseCaseSpy()
        val sut = makeSUT(searchFoodItems = searchFoodItems)
        sut.searchText.value = "tv"
        val firstSearch = launch { sut.onSearchTextChanged() }
        yield()
        firstSearch.cancelAndJoin()
        sut.searchText.value = "tvaroh"

        sut.onSearchTextChanged()

        assertEquals(listOf("tvaroh"), searchFoodItems.queries)
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
            submitFoodItem = SubmitFoodItemUseCaseFake(),
            fetchMySubmissions = FetchMySubmissionsUseCaseFake(),
            updateMySubmission = UpdateMySubmissionUseCaseFake(),
            deleteMySubmission = DeleteMySubmissionUseCaseFake(),
            searchFoodExternally = SearchFoodExternallyUseCaseFake(),
            fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCaseFake(),
            fetchFoodByBarcodeExternally = FetchFoodByBarcodeExternallyUseCaseFake(),
            fetchFavouriteFoods = FetchFavouriteFoodsUseCaseFake(),
            refreshFavouriteFood = RefreshFavouriteFoodUseCaseFake(),
            fetchMyCreatedMeals = FetchMyCreatedMealsUseCaseFake(),
            deleteMyCreatedMeal = DeleteMyCreatedMealUseCaseFake(),
            recognizeNutritionLabelUseCase = RecognizeNutritionLabelUseCaseFake(),
            onFoodSaved = { onFoodSavedCalled = true },
        )

        sut.onFoodConsumedSaved()

        assertTrue(onFoodSavedCalled)
        assertTrue(sut.shouldDismiss.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        searchFoodItems: SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        submitFoodItem: SubmitFoodItemUseCaseProtocol = SubmitFoodItemUseCaseFake(),
        fetchMySubmissions: FetchMySubmissionsUseCaseProtocol = FetchMySubmissionsUseCaseFake(),
        updateMySubmission: UpdateMySubmissionUseCaseProtocol = UpdateMySubmissionUseCaseFake(),
        deleteMySubmission: DeleteMySubmissionUseCaseProtocol = DeleteMySubmissionUseCaseFake(),
        searchFoodExternally: SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        fetchFavouriteFoods: FetchFavouriteFoodsUseCaseProtocol = FetchFavouriteFoodsUseCaseFake(),
        refreshFavouriteFood: RefreshFavouriteFoodUseCaseProtocol = RefreshFavouriteFoodUseCaseFake(),
        fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseProtocol = FetchMyCreatedMealsUseCaseFake(),
        deleteMyCreatedMeal: DeleteMyCreatedMealUseCaseProtocol = DeleteMyCreatedMealUseCaseFake(),
        recognizeNutritionLabel: RecognizeNutritionLabelUseCaseProtocol = RecognizeNutritionLabelUseCaseFake(),
        isScannerVisible: Boolean = false,
    ): AddFoodSheetViewModel = AddFoodSheetViewModel(
        searchFoodItems = searchFoodItems,
        submitFoodItem = submitFoodItem,
        fetchMySubmissions = fetchMySubmissions,
        updateMySubmission = updateMySubmission,
        deleteMySubmission = deleteMySubmission,
        searchFoodExternally = searchFoodExternally,
        fetchFoodItemByBarcode = fetchFoodItemByBarcode,
        fetchFoodByBarcodeExternally = fetchFoodByBarcodeExternally,
        fetchFavouriteFoods = fetchFavouriteFoods,
        refreshFavouriteFood = refreshFavouriteFood,
        fetchMyCreatedMeals = fetchMyCreatedMeals,
        deleteMyCreatedMeal = deleteMyCreatedMeal,
        recognizeNutritionLabelUseCase = recognizeNutritionLabel,
        isScannerVisible = isScannerVisible,
    )

    private fun fillValidForm(sut: AddFoodSheetViewModel, scannedCode: String) {
        sut.formInput.value = sut.formInput.value.copy(
            scannedCode = scannedCode,
            name = "Tvaroh",
            weightOfProduct = 200.0,
            caloriesPerHundredGrams = 80.0,
        )
    }

    private fun makeSubmission(
        id: String = "sub-1",
        barcode: String,
        status: FoodItemSubmissionStatus,
        rejectReason: String? = null,
    ): FoodItemSubmissionDomain = FoodItemSubmissionDomain(
        id = id,
        barcode = barcode,
        submittedBy = "test-user",
        status = status,
        submittedAt = Instant.now(),
        rejectReason = rejectReason,
        item = makeFoodItem(id = barcode, czName = "Ovar"),
    )

    private class SearchFoodItemsUseCaseSpy : SearchFoodItemsUseCaseProtocol {
        val queries = mutableListOf<String>()

        override suspend fun invoke(query: String): List<FoodItemDomain> {
            queries += query
            return emptyList()
        }
    }

    private class SubmitFoodItemUseCaseSpy : SubmitFoodItemUseCaseProtocol {
        var receivedItem: FoodItemDomain? = null

        override suspend fun invoke(item: FoodItemDomain): FoodItemSubmissionDomain {
            receivedItem = item
            return FoodItemSubmissionDomain(
                id = "new-id",
                barcode = item.id,
                submittedBy = "test-user",
                status = FoodItemSubmissionStatus.PENDING,
                submittedAt = Instant.now(),
                rejectReason = null,
                item = item,
            )
        }
    }

    private class UpdateMySubmissionUseCaseSpy : UpdateMySubmissionUseCaseProtocol {
        var receivedId: String? = null

        override suspend fun invoke(id: String, item: FoodItemDomain): FoodItemSubmissionDomain {
            receivedId = id
            return FoodItemSubmissionDomain(
                id = id,
                barcode = item.id,
                submittedBy = "test-user",
                status = FoodItemSubmissionStatus.PENDING,
                submittedAt = Instant.now(),
                rejectReason = null,
                item = item,
            )
        }
    }

    private class DeleteMySubmissionUseCaseSpy(private val errorToThrow: Exception? = null) : DeleteMySubmissionUseCaseProtocol {
        var receivedId: String? = null

        override suspend fun invoke(id: String) {
            receivedId = id
            errorToThrow?.let { throw it }
        }
    }

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
