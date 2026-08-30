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

    // MARK: - Helpers

    private func makeSUT(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        fetchFavouriteFoods: any FetchFavouriteFoodsUseCaseProtocol = FetchFavouriteFoodsUseCaseFake(),
        fetchMyCreatedMeals: any FetchMyCreatedMealsUseCaseProtocol = FetchMyCreatedMealsUseCaseFake(),
        isScannerVisible: Bool = false
    ) -> AddFoodSheetViewModel {
        AddFoodSheetViewModel(
            searchFoodItems: searchFoodItems,
            createFoodItem: CreateFoodItemUseCaseFake(),
            searchFoodExternally: searchFoodExternally,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            fetchFoodByBarcodeExternally: fetchFoodByBarcodeExternally,
            fetchFavouriteFoods: fetchFavouriteFoods,
            fetchMyCreatedMeals: fetchMyCreatedMeals,
            isScannerVisible: isScannerVisible
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
