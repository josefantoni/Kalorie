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

    func test_grams_withOneHundredGramUnit_is100() {
        let sut = makeSUT()
        sut.unit = .hundredGrams
        sut.quantity = 1
        XCTAssertEqual(sut.grams, 100)
    }

    func test_grams_withTwoHundredGrams_is200() {
        let sut = makeSUT()
        sut.unit = .hundredGrams
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

    func test_scaledCalories_withOneHundredGramOf200kcalItem_is200() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 200))
        sut.unit = .hundredGrams
        sut.quantity = 1
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    func test_savesRoundedCalories_whenScalingProducesFraction() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 133))
        sut.unit = .grams
        sut.quantity = 150
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    func test_scaledCalories_whenCaloriesPerHundredGramsIsFractional_roundsOnce() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 133.6))
        sut.unit = .grams
        sut.quantity = 150
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    // MARK: - scaledFiber

    func test_scaledFiber_whenItemsFiberIsUnknown_showsZeroInsteadOfNil() {
        let sut = makeSUT(item: makeFoodItem(fiber: nil))
        XCTAssertEqual(sut.scaledFiber, 0)
    }

    // MARK: - init defaults

    func test_init_withoutQuantityOrUnit_defaultsToOneHundredGram() {
        let sut = makeSUT()
        XCTAssertEqual(sut.quantity, 1)
        XCTAssertEqual(sut.unit, .hundredGrams)
    }

    func test_init_withExplicitQuantityAndUnit_usesThemInsteadOfTheDefault() {
        let sut = makeSUT(quantity: 340, unit: .grams)
        XCTAssertEqual(sut.quantity, 340)
        XCTAssertEqual(sut.unit, .grams)
    }

    // MARK: - onUnitChanged

    func test_onUnitChanged_toGrams_convertsQuantity() {
        let sut = makeSUT()
        sut.quantity = 1
        sut.onUnitChanged(from: .hundredGrams, to: .grams)
        XCTAssertEqual(sut.quantity, 100)
    }

    func test_onUnitChanged_toHundredGrams_convertsQuantity() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 200
        sut.onUnitChanged(from: .grams, to: .hundredGrams)
        XCTAssertEqual(sut.quantity, 2)
    }

    func test_onUnitChanged_toHundredGrams_keepsFraction() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 150
        sut.onUnitChanged(from: .grams, to: .hundredGrams)
        XCTAssertEqual(sut.quantity, 1.5)
    }

    func test_onUnitChanged_toHundredGrams_belowFiftyGrams_doesNotFloorToOne() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 20
        sut.onUnitChanged(from: .grams, to: .hundredGrams)
        XCTAssertEqual(sut.quantity, 0.2)
    }

    func test_onUnitChanged_toHundredGramsAndBack_roundTripsExactly() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 150
        sut.onUnitChanged(from: .grams, to: .hundredGrams)
        sut.onUnitChanged(from: .hundredGrams, to: .grams)
        XCTAssertEqual(sut.quantity, 150)
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

    @MainActor
    func test_onConfirm_usesFreshlyFetchedMealTypesInsteadOfStaleSnapshot() async {
        let freshMealTypes = [MealTypeDomain(id: "lunch", name: "Lunch", startTime: .now, endTime: .now)]
        let spy = SaveFoodConsumedUseCaseSpy()
        let sut = makeSUT(saveFoodConsumed: spy, fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: freshMealTypes))
        await sut.onConfirm()
        XCTAssertEqual(
            spy.capturedMealTypes?.map(\.id),
            ["lunch"],
            "onConfirm must resolve the meal-type pin against meal types fetched at save time, not the array captured when the sheet was opened"
        )
    }

    @MainActor
    func test_onConfirm_whenMealTypesRefetchFails_fallsBackToOriginalSnapshot() async {
        let originalMealTypes = [MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: .now, endTime: .now)]
        let spy = SaveFoodConsumedUseCaseSpy()
        let sut = makeSUT(saveFoodConsumed: spy, fetchMealTypes: FetchMealTypesUseCaseFake(shouldThrow: true), mealTypes: originalMealTypes)
        await sut.onConfirm()
        XCTAssertEqual(spy.capturedMealTypes?.map(\.id), ["breakfast"], "a failed refetch must fall back to the snapshot captured when the sheet was opened, not discard it")
        XCTAssertNil(sut.alertItem)
    }

    // MARK: - onAppear (personal portions)

    @MainActor
    func test_onAppear_withCatalogueItem_fetchesPersonalPortions() async {
        let stubbedPortion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(
            item: makeFoodItem(kind: .catalogue),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [stubbedPortion])
        )
        await sut.onAppear()
        XCTAssertEqual(sut.personalPortions, [stubbedPortion])
    }

    @MainActor
    func test_onAppear_withExternalItem_doesNotSurfacePersonalPortions() async {
        let stubbedPortion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(
            item: makeFoodItem(kind: .external),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [stubbedPortion])
        )
        await sut.onAppear()
        XCTAssertTrue(
            sut.personalPortions.isEmpty,
            "OpenFoodFacts items have no reliable package size to key a personal portion off (design 0008)"
        )
    }

    // MARK: - onAddPersonalPortion

    @MainActor
    func test_onAddPersonalPortion_whenSaveSucceeds_appendsToPersonalPortions() async {
        let sut = makeSUT()
        await sut.onAddPersonalPortion(name: "1 balení", grams: 33)
        XCTAssertEqual(sut.personalPortions, [FoodPortionDomain(name: "1 balení", grams: 33)])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onAddPersonalPortion_whenSaveFails_restoresPreAddSnapshotEvenWithADuplicateNameAndGrams() async {
        let existing = FoodPortionDomain(name: "1 lžíce", grams: 15)
        let sut = makeSUT(
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [existing]),
            saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()
        await sut.onAddPersonalPortion(name: existing.name, grams: existing.grams)
        XCTAssertEqual(
            sut.personalPortions,
            [existing],
            "a failed save must restore the pre-add snapshot, not filter by (name, grams) equality — " +
                "a value-based filter also drops an already-saved portion that happens to share the new one's name and grams"
        )
        XCTAssertEqual(sut.alertItem?.title, L10n.MyPortions.errorSaveFailed)
    }

    // MARK: - onDeletePersonalPortion

    @MainActor
    func test_onDeletePersonalPortion_whenSaveSucceeds_removesPortion() async {
        let portion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [portion]))
        await sut.onAppear()
        await sut.onDeletePersonalPortion(portion)
        XCTAssertTrue(sut.personalPortions.isEmpty)
    }

    @MainActor
    func test_onDeletePersonalPortion_whenSaveFails_restoresPortionAndShowsAlert() async {
        let portion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [portion]),
            saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()
        await sut.onDeletePersonalPortion(portion)
        XCTAssertEqual(sut.personalPortions, [portion])
        XCTAssertEqual(sut.alertItem?.title, L10n.MyPortions.errorDeleteFailed)
    }

    // MARK: - Helpers

    private func makeSUT(
        item: FoodItemDomain? = nil,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol = SaveFoodConsumedUseCaseFake(),
        fetchMealTypes: any FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        mealTypes: [MealTypeDomain] = [],
        isFavourite: Bool = false,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        fetchFoodItemPersonalPortions: any FetchFoodItemPersonalPortionsUseCaseProtocol = FetchFoodItemPersonalPortionsUseCaseFake(),
        saveFoodItemPersonalPortions: any SaveFoodItemPersonalPortionsUseCaseProtocol = SaveFoodItemPersonalPortionsUseCaseFake(),
        onSaved: @escaping () -> Void = {},
        onFavouriteChanged: @escaping (String, Bool) -> Void = { _, _ in },
        quantity: Double = 1,
        unit: FoodQuantityUnit = .hundredGrams
    ) -> FoodQuantityViewModel {
        let sut = FoodQuantityViewModel(
            item: item ?? makeFoodItem(),
            saveFoodConsumed: saveFoodConsumed,
            fetchMealTypes: fetchMealTypes,
            selectedDate: .now,
            mealTypes: mealTypes,
            isFavourite: isFavourite,
            addFavouriteFood: addFavouriteFood,
            removeFavouriteFood: removeFavouriteFood,
            fetchFoodItemPersonalPortions: fetchFoodItemPersonalPortions,
            saveFoodItemPersonalPortions: saveFoodItemPersonalPortions,
            onSaved: onSaved,
            onFavouriteChanged: onFavouriteChanged,
            quantity: quantity,
            unit: unit
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "FoodQuantityViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeFoodItem(kind: FoodItemKind = .catalogue, caloriesPerHundredGrams: Double = 100, fiber: Double? = 0) -> FoodItemDomain {
        FoodItemDomain(
            id: "test",
            kind: kind,
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
            fiber: fiber,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class SaveFoodConsumedUseCaseSpy: SaveFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    private(set) var capturedMealTypes: [MealTypeDomain]?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, grams: Double, date: Date, mealTypes: [MealTypeDomain]) async throws {
        capturedMealTypes = mealTypes
    }
}
