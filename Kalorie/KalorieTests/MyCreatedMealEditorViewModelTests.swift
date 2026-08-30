//
//  MyCreatedMealEditorViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 20.08.2026.
//

import XCTest
@testable import Kalorie

final class MyCreatedMealEditorViewModelTests: XCTestCase {

    // MARK: - canSave

    func test_canSave_withNoIngredients_isFalse() {
        let sut = makeSUT()
        XCTAssertFalse(sut.canSave)
    }

    func test_canSave_withNameAndIngredientWithValidGrams_isTrue() {
        let sut = makeSUT()
        sut.name = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.ingredients[0].gramsText = "50"
        XCTAssertTrue(sut.canSave)
    }

    func test_canSave_withUnparseableGrams_isFalse() {
        let sut = makeSUT()
        sut.name = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.ingredients[0].gramsText = ""
        XCTAssertFalse(sut.canSave)
    }

    func test_canSave_whenEditingWithNoChanges_isFalse() {
        let existingMeal = MyCreatedMealDomain(id: "1", name: "Kaše", ingredients: [makeIngredientDomain()], createdAt: .now, updatedAt: .now)
        let sut = makeSUT(existingMeal: existingMeal)
        XCTAssertFalse(sut.canSave, "opening an existing meal for editing must not enable Save until the user actually changes something")
    }

    func test_canSave_whenEditingWithNameChanged_isTrue() {
        let existingMeal = MyCreatedMealDomain(id: "1", name: "Kaše", ingredients: [makeIngredientDomain()], createdAt: .now, updatedAt: .now)
        let sut = makeSUT(existingMeal: existingMeal)
        sut.name = "Jiná kaše"
        XCTAssertTrue(sut.canSave)
    }

    func test_canSave_whenEditingWithGramsChanged_isTrue() {
        let existingMeal = MyCreatedMealDomain(id: "1", name: "Kaše", ingredients: [makeIngredientDomain()], createdAt: .now, updatedAt: .now)
        let sut = makeSUT(existingMeal: existingMeal)
        sut.ingredients[0].gramsText = "75"
        XCTAssertTrue(sut.canSave)
    }

    // MARK: - onSelectSearchResult / onDeleteIngredient

    func test_onSelectSearchResult_appendsIngredientWithoutNavigating() {
        let sut = makeSUT()
        sut.onSelectSearchResult(makeFoodItem(id: "1"))
        sut.onSelectSearchResult(makeFoodItem(id: "2"))
        XCTAssertEqual(sut.ingredients.map { $0.item.id }, ["1", "2"])
    }

    func test_onSelectSearchResult_clearsSearchTextAndResults() async {
        let sut = makeSUT(searchFoodItems: SearchFoodItemsUseCaseFake(stubbedItems: [makeFoodItem()]))
        sut.searchText = "ovesné"
        await sut.onSearchTextChanged()
        XCTAssertFalse(sut.searchResults.isEmpty)

        sut.onSelectSearchResult(makeFoodItem())

        XCTAssertEqual(sut.searchText, "", "picking a result should not leave the query visible, or the results section open, underneath the row it just added")
        XCTAssertTrue(sut.searchResults.isEmpty)
    }

    func test_onSelectSearchResult_returnsTheNewDraftsId() {
        let sut = makeSUT()
        let id = sut.onSelectSearchResult(makeFoodItem())
        XCTAssertEqual(sut.ingredients.last?.id, id, "the view needs the new row's id to move keyboard focus onto it")
    }

    func test_onDeleteIngredient_removesRowAtOffset() {
        let sut = makeSUT()
        sut.onSelectSearchResult(makeFoodItem(id: "1"))
        sut.onSelectSearchResult(makeFoodItem(id: "2"))
        sut.onDeleteIngredient(at: IndexSet(integer: 0))
        XCTAssertEqual(sut.ingredients.map { $0.item.id }, ["2"])
    }

    // MARK: - onScannerButtonTapped / onBarcodeScanned

    func test_onScannerButtonTapped_makesScannerVisible() {
        let sut = makeSUT()
        sut.onScannerButtonTapped()
        XCTAssertTrue(sut.isScannerVisible)
    }

    func test_onBarcodeScanned_withEmptyBarcode_doesNothing() async {
        let sut = makeSUT()
        sut.lastScannedBarcode = ""
        await sut.onBarcodeScanned()
        XCTAssertNil(sut.alertItem)
        XCTAssertTrue(sut.ingredients.isEmpty)
    }

    func test_onBarcodeScanned_whenNotFoundLocallyOrExternally_showsNotFoundAlert() async {
        let sut = makeSUT()
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorBarcodeNotFound)
        XCTAssertTrue(sut.ingredients.isEmpty)
    }

    func test_onBarcodeScanned_whenLocalFound_appendsIngredientAndHidesScanner() async {
        let item = makeFoodItem(id: "8594004428464")
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item))
        sut.isScannerVisible = true
        sut.lastScannedBarcode = "8594004428464"

        await sut.onBarcodeScanned()

        XCTAssertEqual(sut.ingredients.map { $0.item.id }, ["8594004428464"])
        XCTAssertFalse(sut.isScannerVisible)
        XCTAssertEqual(sut.scannedIngredientId, sut.ingredients.first?.id, "the view needs this to move keyboard focus onto the new row, same as a tapped search result")
        XCTAssertNil(sut.alertItem)
    }

    func test_onBarcodeScanned_whenOnlyExternalFound_appendsIngredientAndHidesScanner() async {
        let item = makeFoodItem(id: "8594004428464")
        let sut = makeSUT(fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem: item))
        sut.isScannerVisible = true
        sut.lastScannedBarcode = "8594004428464"

        await sut.onBarcodeScanned()

        XCTAssertEqual(sut.ingredients.map { $0.item.id }, ["8594004428464"])
        XCTAssertFalse(sut.isScannerVisible)
    }

    func test_onBarcodeScanned_whenExternalFails_showsLoadFailedAlertAndDoesNotAppend() async {
        let sut = makeSUT(fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(shouldThrow: true))
        sut.lastScannedBarcode = "8594004428464"
        await sut.onBarcodeScanned()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorLoadFailed)
        XCTAssertTrue(sut.ingredients.isEmpty)
    }

    // MARK: - onSearchTextChanged (external fallback)

    func test_onSearchTextChanged_whenLocalEmptyAndQueryLongEnough_fallsBackToExternal() async {
        let externalItem = makeFoodItem(id: "off-1")
        let sut = makeSUT(
            searchFoodItems: SearchFoodItemsUseCaseFake(stubbedItems: []),
            searchFoodExternally: SearchFoodExternallyUseCaseFake(stubbedItems: [externalItem])
        )
        sut.searchText = "tvaroh"

        await sut.onSearchTextChanged()

        XCTAssertEqual(
            sut.externalSearchResults.map(\.id),
            ["off-1"],
            "a food only on OpenFoodFacts must still be reachable as an ingredient"
        )
    }

    func test_onSearchTextChanged_whenLocalResultsExist_doesNotFallBackToExternal() async {
        let sut = makeSUT(
            searchFoodItems: SearchFoodItemsUseCaseFake(stubbedItems: [makeFoodItem()]),
            searchFoodExternally: SearchFoodExternallyUseCaseFake(stubbedItems: [makeFoodItem(id: "off-1")])
        )
        sut.searchText = "ovesné"

        await sut.onSearchTextChanged()

        XCTAssertTrue(sut.externalSearchResults.isEmpty, "the catalogue already answered the query — hitting OpenFoodFacts on top would be a wasted network call")
    }

    // MARK: - onGramsFieldDefocused

    func test_onGramsFieldDefocused_withEmptyGrams_fillsDefaultOfHundred() {
        let sut = makeSUT()
        let id = sut.onSelectSearchResult(makeFoodItem())

        sut.onGramsFieldDefocused(id: id)

        XCTAssertEqual(sut.ingredients.first?.gramsText, "100", "leaving a freshly added row untouched should fall back to a sane default instead of blocking Save with no explanation")
    }

    func test_onGramsFieldDefocused_withUserEnteredGrams_doesNotOverwrite() {
        let sut = makeSUT()
        let id = sut.onSelectSearchResult(makeFoodItem())
        sut.ingredients[0].gramsText = "50"

        sut.onGramsFieldDefocused(id: id)

        XCTAssertEqual(sut.ingredients.first?.gramsText, "50")
    }

    // MARK: - onSaveTapped

    func test_onSaveTapped_whenCannotSave_doesNotShowConfirmation() {
        let sut = makeSUT()
        sut.onSaveTapped()
        XCTAssertFalse(sut.isSaveConfirmationVisible)
    }

    func test_onSaveTapped_whenCanSave_showsConfirmation() {
        let sut = makeSUT()
        sut.name = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.ingredients[0].gramsText = "50"
        sut.onSaveTapped()
        XCTAssertTrue(sut.isSaveConfirmationVisible)
    }

    // MARK: - onSaveConfirmed (create)

    @MainActor
    func test_onSaveConfirmed_whenCreating_createsMealAndDismisses() async {
        var onSavedCalled = false
        let sut = makeSUT { onSavedCalled = true }
        sut.name = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.ingredients[0].gramsText = "50"

        await sut.onSaveConfirmed()

        XCTAssertTrue(onSavedCalled)
        XCTAssertTrue(sut.shouldDismiss)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onSaveConfirmed_whenCreateFails_showsAlertAndDoesNotDismiss() async {
        let sut = makeSUT(createMyCreatedMeal: CreateMyCreatedMealUseCaseFake(shouldThrow: true))
        sut.name = "Kaše"
        sut.onSelectSearchResult(makeFoodItem())
        sut.ingredients[0].gramsText = "50"

        await sut.onSaveConfirmed()

        XCTAssertNotNil(sut.alertItem)
        XCTAssertFalse(sut.shouldDismiss)
    }

    // MARK: - onSaveConfirmed (edit)

    @MainActor
    func test_onSaveConfirmed_whenEditing_preservesCreatedAt() async {
        var updatedMeal: MyCreatedMealDomain?
        let updateUseCase = UpdateMyCreatedMealUseCaseSpy { updatedMeal = $0 }
        let originalCreatedAt = Date(timeIntervalSince1970: 1_000)
        let existingMeal = MyCreatedMealDomain(
            id: "meal-1",
            name: "Kaše",
            ingredients: [makeIngredientDomain()],
            createdAt: originalCreatedAt,
            updatedAt: originalCreatedAt
        )
        let sut = makeSUT(updateMyCreatedMeal: updateUseCase, existingMeal: existingMeal)

        await sut.onSaveConfirmed()

        XCTAssertEqual(updatedMeal?.id, "meal-1")
        XCTAssertEqual(updatedMeal?.createdAt, originalCreatedAt)
        XCTAssertTrue(sut.shouldDismiss)
    }

    func test_isEditing_reflectsWhetherAnExistingMealWasPassed() {
        let sut = makeSUT()
        XCTAssertFalse(sut.isEditing)

        let editingSUT = makeSUT(existingMeal: MyCreatedMealDomain(id: "1", name: "Kaše", ingredients: [makeIngredientDomain()], createdAt: .now, updatedAt: .now))
        XCTAssertTrue(editingSUT.isEditing)
    }

    // MARK: - Helpers

    private func makeSUT(
        searchFoodItems: any SearchFoodItemsUseCaseProtocol = SearchFoodItemsUseCaseFake(),
        searchFoodExternally: any SearchFoodExternallyUseCaseProtocol = SearchFoodExternallyUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        createMyCreatedMeal: any CreateMyCreatedMealUseCaseProtocol = CreateMyCreatedMealUseCaseFake(),
        updateMyCreatedMeal: any UpdateMyCreatedMealUseCaseProtocol = UpdateMyCreatedMealUseCaseFake(),
        existingMeal: MyCreatedMealDomain? = nil,
        onSaved: @escaping () -> Void = {}
    ) -> MyCreatedMealEditorViewModel {
        let sut = MyCreatedMealEditorViewModel(
            searchFoodItems: searchFoodItems,
            searchFoodExternally: searchFoodExternally,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            fetchFoodByBarcodeExternally: fetchFoodByBarcodeExternally,
            createMyCreatedMeal: createMyCreatedMeal,
            updateMyCreatedMeal: updateMyCreatedMeal,
            existingMeal: existingMeal,
            onSaved: onSaved
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "MyCreatedMealEditorViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeFoodItem(id: String = "12345") -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: "Ovesné vločky",
            engName: "Oats",
            weight: 100,
            date: .now,
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
    }

    private func makeIngredientDomain() -> MyCreatedMealIngredientDomain {
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
    }
}

private final class UpdateMyCreatedMealUseCaseSpy: UpdateMyCreatedMealUseCaseProtocol {

    // MARK: - Properties

    private let onUpdate: (MyCreatedMealDomain) -> Void

    // MARK: - Init

    init(onUpdate: @escaping (MyCreatedMealDomain) -> Void) {
        self.onUpdate = onUpdate
    }

    // MARK: - Functions

    func callAsFunction(_ meal: MyCreatedMealDomain) async throws {
        onUpdate(meal)
    }
}
