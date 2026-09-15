//
//  ModerationCatalogueEditorViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 11.09.2026.
//

import XCTest
@testable import Kalorie

final class ModerationCatalogueEditorViewModelTests: XCTestCase {

    // MARK: - onSearchTapped

    @MainActor
    func test_onSearchTapped_whenFound_prefillsFormAndClearsPreviousSavedFlag() async {
        let item = makeItem()
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item))
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()
        XCTAssertEqual(sut.formInput.scannedCode, "12345678")
        XCTAssertFalse(sut.didSave)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onSearchTapped_whenNotFound_showsAlert() async {
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil))
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorBarcodeNotFound)
    }

    // MARK: - onSaveTapped

    @MainActor
    func test_onSaveTapped_preservesTheOriginalItemsDate() async {
        let originalDate = Date(timeIntervalSince1970: 1_700_000_000)
        let item = makeItem(date: originalDate)
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            updateFoodItem: updateFoodItem
        )
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()

        sut.formInput.name = "Opravený název"
        await sut.onSaveTapped()

        XCTAssertEqual(updateFoodItem.receivedItem?.date, originalDate, "correcting a field must not silently reset the item's original date")
        XCTAssertTrue(sut.didSave)
    }

    // MARK: - onNutritionLabelCaptured

    @MainActor
    func test_onNutritionLabelCaptured_onSuccess_closesCameraAndMergesWithoutTouchingFilledFields() async {
        let item = makeItem()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(stubbedReading: NutritionLabelReading(fat: 999, fiber: 1))
        )
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()
        sut.isNutritionLabelCameraVisible = true
        let originalFat = sut.formInput.fat

        await sut.onNutritionLabelCaptured(UIImage(), liveBarcode: nil)

        XCTAssertFalse(sut.isNutritionLabelCameraVisible, "a successful capture on an already-open form must close the camera without a push")
        XCTAssertEqual(sut.formInput.fat, originalFat, "a field already holding a loaded value must never be overwritten by the photo")
        XCTAssertEqual(sut.formInput.fiber, 1, "a field still at its default must be filled")
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        updateFoodItem: any UpdateFoodItemUseCaseProtocol = UpdateFoodItemUseCaseFake(),
        recognizeNutritionLabel: any RecognizeNutritionLabelUseCaseProtocol = RecognizeNutritionLabelUseCaseFake(),
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProviderFake()
    ) -> ModerationCatalogueEditorViewModel {
        ModerationCatalogueEditorViewModel(
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            updateFoodItem: updateFoodItem,
            recognizeNutritionLabel: recognizeNutritionLabel,
            cameraAuthorizationProvider: cameraAuthorizationProvider
        )
    }

    private func makeItem(id: String = "12345678", date: Date = .now) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: "Tvaroh",
            engName: "Cottage cheese",
            weight: 200,
            date: date,
            energyKJ: 335,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: 0.3,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class UpdateFoodItemUseCaseSpy: UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    private(set) var receivedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws {
        receivedItem = item
    }
}
