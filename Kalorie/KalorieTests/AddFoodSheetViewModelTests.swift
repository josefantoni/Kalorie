//
//  AddFoodSheetViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class AddFoodSheetViewModelTests: XCTestCase {

    // MARK: - onScannerButtonTapped

    func test_onScannerButtonTapped_makesScannerVisible() {
        let sut = makeSUT()
        sut.onScannerButtonTapped()
        XCTAssertTrue(sut.isScannerVisible)
        XCTAssertFalse(sut.isAddNewItemVisible)
    }

    func test_onScannerButtonTapped_whenScannerAlreadyVisible_keepsScannerVisible() {
        let sut = makeSUT(isScannerVisible: true)
        sut.onScannerButtonTapped()
        XCTAssertTrue(sut.isScannerVisible)
    }

    // MARK: - onScenePhaseActive

    func test_onScenePhaseActive_whenScannerVisibleAndCameraStillAvailable_keepsScannerVisible() {
        let sut = makeSUT(isScannerVisible: true)
        sut.onScenePhaseActive(isCameraAvailable: true)
        XCTAssertTrue(sut.isScannerVisible)
        XCTAssertNil(sut.alertItem)
    }

    func test_onScenePhaseActive_whenScannerVisibleAndCameraNoLongerAvailable_hidesScannerAndShowsAlert() {
        let sut = makeSUT(isScannerVisible: true)
        sut.onScenePhaseActive(isCameraAvailable: false)
        XCTAssertFalse(sut.isScannerVisible)
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.cameraPermissionAlert)
    }

    func test_onScenePhaseActive_whenScannerNotVisible_doesNothing() {
        let sut = makeSUT(isScannerVisible: false)
        sut.onScenePhaseActive(isCameraAvailable: false)
        XCTAssertFalse(sut.isScannerVisible)
        XCTAssertNil(sut.alertItem)
    }

    // MARK: - onBarcodeScanned

    func test_onBarcodeScanned_withEmptyBarcode_doesNothing() async {
        let sut = makeSUT()
        sut.lastScannedBarcode = ""
        await sut.onBarcodeScanned()
        XCTAssertNil(sut.alertItem)
        XCTAssertFalse(sut.isBarcodeSearchLoading)
    }

    func test_onBarcodeScanned_whenNotFound_showsNotFoundAlert() async {
        let sut = makeSUT()
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorBarcodeNotFound)
    }

    func test_onBarcodeScanned_whenNotFound_stopsLoading() async {
        let sut = makeSUT()
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertFalse(sut.isBarcodeSearchLoading)
    }

    func test_onBarcodeScanned_whenLocalFound_navigatesToQuantityView() async {
        let item = makeFoodItem(id: "8594004428464")
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item))
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertTrue(sut.isPushedToQuantityView)
        XCTAssertFalse(sut.isScannerVisible)
        XCTAssertNil(sut.alertItem)
    }

    func test_onBarcodeScanned_whenExternalFound_navigatesToQuantityView() async {
        let item = makeFoodItem(id: "8594004428464")
        let sut = makeSUT(fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem: item))
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertTrue(sut.isPushedToQuantityView)
        XCTAssertFalse(sut.isScannerVisible)
    }

    func test_onBarcodeScanned_whenExternalFails_showsLoadFailedAlert() async {
        let sut = makeSUT(fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(shouldThrow: true))
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorLoadFailed)
    }

    // MARK: - onSearchTextChanged

    func test_onSearchTextChanged_whenLocalSearchFails_localItemsAreEmptyAndNoAlert() async {
        let sut = makeSUT(searchFoodItems: SearchFoodItemsUseCaseFake(shouldThrow: true))
        sut.searchText = "tvaroh"
        await sut.onSearchTextChanged()
        XCTAssertTrue(sut.localFoodItems.isEmpty)
        XCTAssertNil(sut.alertItem)
    }

    func test_onSearchTextChanged_whenExternalSearchFails_externalItemsAreEmptyAndNoAlert() async {
        let sut = makeSUT(searchFoodExternally: SearchFoodExternallyUseCaseFake(shouldThrow: true))
        sut.searchText = "tvaroh"
        await sut.onSearchTextChanged()
        XCTAssertTrue(sut.externalFoodItems.isEmpty)
        XCTAssertNil(sut.alertItem)
    }

    // MARK: - onSelectFoodItem

    @MainActor
    func test_onSelectFoodItem_setsSelectedFoodItemAndNavigates() {
        let sut = makeSUT()
        sut.onSelectFoodItem(makeFoodItem(id: "abc"))
        XCTAssertEqual(sut.selectedFoodItem?.id, "abc")
        XCTAssertTrue(sut.isPushedToQuantityView)
    }

    @MainActor
    func test_onSelectFoodItem_whenCalledTwice_firstItemWins() {
        let sut = makeSUT()
        sut.onSelectFoodItem(makeFoodItem(id: "A"))
        sut.onSelectFoodItem(makeFoodItem(id: "B"))
        XCTAssertEqual(sut.selectedFoodItem?.id, "A")
        XCTAssertTrue(sut.isPushedToQuantityView)
    }

    // MARK: - displayedResults

    @MainActor
    func test_displayedResults_hoistsMatchingCreatedMealsAboveFavouritesAndCatalog() async {
        let sut = makeSUT(
            fetchFavouriteFoods: FetchFavouriteFoodsUseCaseFake(stubbedItems: [makeFoodItem(id: "fav", czName: "Ovar")]),
            fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(stubbedMeals: [makeMeal(id: "meal", name: "Ovesná kaše")])
        )
        await sut.onAppear()
        sut.localFoodItems = [makeFoodItem(id: "cat", czName: "Ovoce")]
        sut.searchText = "ov"
        XCTAssertEqual(sut.displayedResults.map(\.id), ["meal", "fav", "cat"])
    }

    @MainActor
    func test_displayedResults_createdMeal_hasCreatedMealKind() async {
        let sut = makeSUT(fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(stubbedMeals: [makeMeal(id: "meal", name: "Ovesná kaše")]))
        await sut.onAppear()
        sut.searchText = "ov"
        XCTAssertEqual(sut.displayedResults.first(where: { $0.id == "meal" })?.kind, .createdMeal)
    }

    // MARK: - isMyCreatedMeal

    @MainActor
    func test_isMyCreatedMeal_returnsTrueOnlyForCreatedMealKind() {
        let sut = makeSUT()
        XCTAssertTrue(sut.isMyCreatedMeal(makeFoodItem(kind: .createdMeal)))
        XCTAssertFalse(sut.isMyCreatedMeal(makeFoodItem(kind: .catalogue)))
        XCTAssertFalse(sut.isMyCreatedMeal(makeFoodItem(kind: .external)))
    }

    // MARK: - own submissions in displayedResults

    @MainActor
    func test_displayedResults_includesMatchingOwnSubmission() async {
        let sut = makeSUT(fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [makeSubmission(barcode: "sub-item", status: .rejected)]))
        await sut.onAppear()
        sut.searchText = "ov"
        XCTAssertTrue(sut.displayedResults.map(\.id).contains("sub-item"))
    }

    @MainActor
    func test_submissionStatus_forMatchingSubmission_returnsItsStatus() async {
        let sut = makeSUT(fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [makeSubmission(barcode: "sub-item", status: .rejected)]))
        await sut.onAppear()
        XCTAssertEqual(sut.submissionStatus(for: makeFoodItem(id: "sub-item")), .rejected)
        XCTAssertNil(sut.submissionStatus(for: makeFoodItem(id: "other-item")))
    }

    // MARK: - onSelectRejectedSubmission

    @MainActor
    func test_onSelectRejectedSubmission_prefillsFormAndShowsRejectionReason() async {
        let submission = makeSubmission(id: "sub-1", barcode: "sub-item", status: .rejected, rejectReason: "Wrong calories")
        let sut = makeSUT(fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [submission]))
        await sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id: "sub-item", czName: "Ovar"))
        XCTAssertTrue(sut.isAddNewItemVisible)
        XCTAssertEqual(sut.formInput.scannedCode, "sub-item")
        XCTAssertEqual(sut.formInput.name, "Ovar")
        XCTAssertEqual(sut.rejectionReasonBeingEdited, "Wrong calories")
        XCTAssertTrue(sut.isEditingSubmission, "the barcode must be locked while resubmitting, or approving it can orphan entries logged under the old barcode")
    }

    // MARK: - onAddNewItemToggleTapped

    @MainActor
    func test_onAddNewItemToggleTapped_afterClosingRejectedSubmissionEdit_reopensAsFreshFormAndSubmitsNewItem() async {
        let submission = makeSubmission(id: "sub-1", barcode: "sub-item", status: .rejected, rejectReason: "Wrong calories")
        let submitFoodItem = SubmitFoodItemUseCaseSpy()
        let updateMySubmission = UpdateMySubmissionUseCaseSpy()
        let sut = makeSUT(
            submitFoodItem: submitFoodItem,
            fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [submission]),
            updateMySubmission: updateMySubmission
        )
        await sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id: "sub-item", czName: "Ovar"))
        XCTAssertTrue(sut.isAddNewItemVisible)

        sut.onAddNewItemToggleTapped()
        XCTAssertFalse(sut.isAddNewItemVisible, "closing the form must not leave it silently pointed at the old submission")

        sut.onAddNewItemToggleTapped()
        XCTAssertTrue(sut.isAddNewItemVisible)
        XCTAssertNil(sut.rejectionReasonBeingEdited)
        XCTAssertEqual(sut.formInput.scannedCode, "")
        XCTAssertFalse(sut.isEditingSubmission, "a freshly reopened form must allow a barcode again")

        sut.formInput.scannedCode = "99999999"
        sut.formInput.name = "Nová položka"
        sut.formInput.weightOfProduct = 100
        sut.formInput.caloriesPerHundredGrams = 50
        await sut.onCreateFoodItem()

        XCTAssertEqual(submitFoodItem.receivedItem?.id, "99999999", "a freshly reopened form must submit a new submission")
        XCTAssertNil(updateMySubmission.receivedId, "it must not silently overwrite the previously edited submission")
    }

    // MARK: - onSelectSubmission

    @MainActor
    func test_onSelectSubmission_forRejectedSubmission_opensThatExactSubmissionDespiteDuplicateBarcode() async {
        let rejected = makeSubmission(id: "sub-old", barcode: "shared-barcode", status: .rejected, rejectReason: "Wrong calories")
        let pending = makeSubmission(id: "sub-new", barcode: "shared-barcode", status: .pending)
        let sut = makeSUT(fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [pending, rejected]))
        await sut.onAppear()

        sut.onSelectSubmission(rejected)

        XCTAssertTrue(sut.isAddNewItemVisible)
        XCTAssertEqual(sut.rejectionReasonBeingEdited, "Wrong calories")
    }

    @MainActor
    func test_onSelectSubmission_forPendingSubmission_navigatesToFoodItemDespiteDuplicateBarcode() async {
        let rejected = makeSubmission(id: "sub-old", barcode: "shared-barcode", status: .rejected, rejectReason: "Wrong calories")
        let pending = makeSubmission(id: "sub-new", barcode: "shared-barcode", status: .pending)
        let sut = makeSUT(fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [pending, rejected]))
        await sut.onAppear()

        sut.onSelectSubmission(pending)

        XCTAssertTrue(sut.isPushedToQuantityView)
        XCTAssertFalse(sut.isAddNewItemVisible)
    }

    // MARK: - onCreateFoodItem

    @MainActor
    func test_onCreateFoodItem_withNoEditingSubmission_submitsNewFoodItem() async {
        let submitFoodItem = SubmitFoodItemUseCaseSpy()
        let sut = makeSUT(submitFoodItem: submitFoodItem)
        sut.formInput.scannedCode = "12345678"
        sut.formInput.name = "Tvaroh"
        sut.formInput.weightOfProduct = 200
        sut.formInput.caloriesPerHundredGrams = 80
        await sut.onCreateFoodItem()
        XCTAssertEqual(submitFoodItem.receivedItem?.id, "12345678")
        XCTAssertTrue(sut.isSubmissionConfirmationVisible)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onCreateFoodItem_whenResubmittingRejectedSubmission_callsUpdateMySubmission() async {
        let submission = makeSubmission(id: "sub-1", barcode: "sub-item", status: .rejected, rejectReason: "Wrong calories")
        let updateMySubmission = UpdateMySubmissionUseCaseSpy()
        let sut = makeSUT(
            fetchMySubmissions: FetchMySubmissionsUseCaseFake(stubbedSubmissions: [submission]),
            updateMySubmission: updateMySubmission
        )
        await sut.onAppear()
        sut.onSelectRejectedSubmission(makeFoodItem(id: "sub-item", czName: "Ovar"))
        sut.formInput.weightOfProduct = 200
        sut.formInput.caloriesPerHundredGrams = 80

        await sut.onCreateFoodItem()

        XCTAssertEqual(updateMySubmission.receivedId, "sub-1")
        XCTAssertTrue(sut.isSubmissionConfirmationVisible)
        XCTAssertNil(sut.rejectionReasonBeingEdited, "resolving the resubmission must clear the rejection banner")
    }

    @MainActor
    func test_onCreateFoodItem_whenBarcodeAlreadyExists_showsAlertAndKeepsFormOpen() async {
        let sut = makeSUT(submitFoodItem: SubmitFoodItemUseCaseFake(errorToThrow: FoodItemSubmissionError.itemAlreadyExists))
        sut.formInput.scannedCode = "12345678"
        sut.formInput.name = "Tvaroh"
        sut.formInput.weightOfProduct = 200
        sut.formInput.caloriesPerHundredGrams = 80
        await sut.onCreateFoodItem()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorItemAlreadyExists)
        XCTAssertFalse(sut.isSubmissionConfirmationVisible)
    }

    // MARK: - onSubmissionConfirmationDismissed

    @MainActor
    func test_onSubmissionConfirmationDismissed_dismissesSheet() {
        let sut = makeSUT()
        sut.isSubmissionConfirmationVisible = true
        sut.onSubmissionConfirmationDismissed()
        XCTAssertFalse(sut.isSubmissionConfirmationVisible)
        XCTAssertTrue(sut.shouldDismiss)
    }

    // MARK: - Helpers

    private func makeSUT(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        submitFoodItem: any SubmitFoodItemUseCaseProtocol = SubmitFoodItemUseCaseFake(),
        fetchMySubmissions: any FetchMySubmissionsUseCaseProtocol = FetchMySubmissionsUseCaseFake(),
        updateMySubmission: any UpdateMySubmissionUseCaseProtocol = UpdateMySubmissionUseCaseFake(),
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol = FetchFavouriteFoodsUseCaseFake(),
        fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol = FetchMyCreatedMealsUseCaseFake(),
        isScannerVisible: Bool = false
    ) -> AddFoodSheetViewModel {
        AddFoodSheetViewModel(
            searchFoodItems: searchFoodItems,
            submitFoodItem: submitFoodItem,
            fetchMySubmissions: fetchMySubmissions,
            updateMySubmission: updateMySubmission,
            searchFoodExternally: searchFoodExternally,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            fetchFoodByBarcodeExternally: fetchFoodByBarcodeExternally,
            fetchFavouriteFoods: fetchFavouriteFoods,
            fetchMyCreatedMeals: fetchMyCreatedMeals,
            isScannerVisible: isScannerVisible
        )
    }

    private func makeSubmission(id: String = "sub-1", barcode: String = "12345678", status: FoodItemSubmissionStatus = .pending, rejectReason: String? = nil) -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: barcode,
            submittedBy: "test-user",
            status: status,
            submittedAt: .now,
            rejectReason: rejectReason,
            item: makeFoodItem(id: barcode, czName: "Ovar")
        )
    }

    private func makeFoodItem(id: String = "test-id", czName: String = "Tvaroh", kind: FoodItemKind = .catalogue) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: kind,
            czName: czName,
            engName: "Cottage cheese",
            weight: 100,
            date: .now,
            energyKJ: 500,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: 0.2,
            fatUnsaturatedFattyAcids: 0.3,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }

    private func makeMeal(id: String, name: String) -> MyCreatedMealDomain {
        MyCreatedMealDomain(
            id: id,
            name: name,
            ingredients: [
                MyCreatedMealIngredientDomain(
                    foodItemId: "12345",
                    czName: "Ovesné vločky",
                    engName: "Oats",
                    grams: 50,
                    nutrition: FoodNutritionValues(
                        energyKJ: 648,
                        caloriesPerHundredGrams: 155,
                        fat: 10,
                        fatSaturated: 3,
                        fatUnsaturatedFattyAcids: 3,
                        carbohydrate: 1,
                        carbohydratePureSugar: 0,
                        fiber: 0,
                        protein: 13,
                        salt: 0.3
                    )
                )
            ],
            createdAt: .now,
            updatedAt: .now
        )
    }
}

private final class SubmitFoodItemUseCaseSpy: SubmitFoodItemUseCaseProtocol {

    // MARK: - Properties

    private(set) var receivedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemSubmissionDomain {
        receivedItem = item
        return FoodItemSubmissionDomain(id: "new-id", barcode: item.id, submittedBy: "test-user", status: .pending, submittedAt: .now, rejectReason: nil, item: item)
    }
}

private final class UpdateMySubmissionUseCaseSpy: UpdateMySubmissionUseCaseProtocol {

    // MARK: - Properties

    private(set) var receivedId: String?
    private(set) var receivedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain) async throws -> FoodItemSubmissionDomain {
        receivedId = id
        receivedItem = item
        return FoodItemSubmissionDomain(id: id, barcode: item.id, submittedBy: "test-user", status: .pending, submittedAt: .now, rejectReason: nil, item: item)
    }
}
