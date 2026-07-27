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

    func test_onSearchTextChanged_whenLocalSearchFails_showsLoadFailedAlert() async {
        let sut = makeSUT(searchFoodItems: SearchFoodItemsUseCaseFake(shouldThrow: true))
        sut.searchText = "tvaroh"
        await sut.onSearchTextChanged()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorLoadFailed)
    }

    func test_onSearchTextChanged_whenExternalSearchFails_showsLoadFailedAlert() async {
        let sut = makeSUT(searchFoodExternally: SearchFoodExternallyUseCaseFake(shouldThrow: true))
        sut.searchText = "tvaroh"
        await sut.onSearchTextChanged()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorLoadFailed)
    }

    // MARK: - onSelectFoodItem

    @MainActor
    func test_onSelectFoodItem_setsQuantityViewModelAndNavigates() {
        let sut = makeSUT()
        sut.onSelectFoodItem(makeFoodItem(id: "abc"))
        XCTAssertNotNil(sut.selectedQuantityViewModel)
        XCTAssertEqual(sut.selectedQuantityViewModel?.item.id, "abc")
        XCTAssertTrue(sut.isPushedToQuantityView)
    }

    @MainActor
    func test_onSelectFoodItem_whenCalledTwice_firstItemWins() {
        let sut = makeSUT()
        sut.onSelectFoodItem(makeFoodItem(id: "A"))
        sut.onSelectFoodItem(makeFoodItem(id: "B"))
        XCTAssertEqual(sut.selectedQuantityViewModel?.item.id, "A")
        XCTAssertTrue(sut.isPushedToQuantityView)
    }

    // MARK: - Helpers

    private func makeSUT(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        isScannerVisible: Bool = false
    ) -> AddFoodSheetViewModel {
        AddFoodSheetViewModel(
            searchFoodItems: searchFoodItems,
            createFoodItem: CreateFoodItemUseCaseFake(),
            saveFoodConsumed: SaveFoodConsumedUseCaseFake(),
            searchFoodExternally: searchFoodExternally,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            fetchFoodByBarcodeExternally: fetchFoodByBarcodeExternally,
            selectedDate: .now,
            isScannerVisible: isScannerVisible
        )
    }

    private func makeFoodItem(id: String = "test-id") -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            czName: "Tvaroh",
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
}
