//
//  FoodQuantityViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class FoodQuantityViewModelTests: XCTestCase {

    // MARK: - grams

    func test_grams_withOnePortionUnit_is100() {
        let sut = makeSUT()
        sut.unit = .portions
        sut.quantity = 1
        XCTAssertEqual(sut.grams, 100)
    }

    func test_grams_withTwoPortions_is200() {
        let sut = makeSUT()
        sut.unit = .portions
        sut.quantity = 2
        XCTAssertEqual(sut.grams, 200)
    }

    func test_grams_withGramsUnit_equalsQuantity() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 150
        XCTAssertEqual(sut.grams, 150)
    }

    // MARK: - scaledCalories

    func test_scaledCalories_calculatesFromGrams() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 200))
        sut.unit = .grams
        sut.quantity = 250
        XCTAssertEqual(sut.scaledCalories, 500)
    }

    func test_scaledCalories_withOnePortionOf200kcalItem_is200() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 200))
        sut.unit = .portions
        sut.quantity = 1
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    // MARK: - onUnitChanged

    @MainActor
    func test_onUnitChanged_toGrams_resetsQuantityTo100() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.onUnitChanged()
        XCTAssertEqual(sut.quantity, 100)
    }

    @MainActor
    func test_onUnitChanged_toPortions_resetsQuantityTo1() {
        let sut = makeSUT()
        sut.unit = .portions
        sut.onUnitChanged()
        XCTAssertEqual(sut.quantity, 1)
    }

    // MARK: - onConfirm

    @MainActor
    func test_onConfirm_withZeroQuantity_showsInvalidQuantityAlert() async {
        let sut = makeSUT()
        sut.quantity = 0
        await sut.onConfirm()
        XCTAssertEqual(sut.alertItem?.title, L10n.FoodQuantity.errorInvalidQuantity)
    }

    @MainActor
    func test_onConfirm_whenSaveSucceeds_callsOnSaved() async {
        var onSavedCalled = false
        let sut = makeSUT { onSavedCalled = true }
        await sut.onConfirm()
        XCTAssertTrue(onSavedCalled)
    }

    @MainActor
    func test_onConfirm_whenSaveSucceeds_setsLoadedState() async {
        let sut = makeSUT()
        await sut.onConfirm()
        XCTAssertFalse(sut.state.isLoading)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onConfirm_whenSaveFails_showsAlert() async {
        let sut = makeSUT(saveFoodConsumed: SaveFoodConsumedUseCaseFake(shouldThrow: true))
        await sut.onConfirm()
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onConfirm_whenSaveFails_doesNotCallOnSaved() async {
        var onSavedCalled = false
        let sut = makeSUT(saveFoodConsumed: SaveFoodConsumedUseCaseFake(shouldThrow: true)) {
            onSavedCalled = true
        }
        await sut.onConfirm()
        XCTAssertFalse(onSavedCalled)
    }

    // MARK: - Helpers

    private func makeSUT(
        item: FoodItemDomain? = nil,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol = SaveFoodConsumedUseCaseFake(),
        onSaved: @escaping () -> Void = {}
    ) -> FoodQuantityViewModel {
        FoodQuantityViewModel(
            item: item ?? makeFoodItem(),
            saveFoodConsumed: saveFoodConsumed,
            selectedDate: .now,
            onSaved: onSaved
        )
    }

    private func makeFoodItem(caloriesPerHundredGrams: Double = 100) -> FoodItemDomain {
        FoodItemDomain(
            id: "test",
            czName: "Tvaroh",
            engName: "Cottage cheese",
            weight: 100,
            date: .now,
            energyKJ: 400,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: 2,
            fatSaturated: 1,
            fatUnsaturatedFattyAcids: 1,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }
}
