//
//  FoodItemFormInputTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodItemFormInputTests: XCTestCase {

    // MARK: - init(item:)

    func test_initFromItem_preservesEnglishName() {
        let item = makeItem(engName: "Cottage cheese")
        let sut = FoodItemFormInput(item: item)
        XCTAssertEqual(sut.engName, "Cottage cheese")
    }

    func test_initFromItem_withNilFatSaturatedAndFiber_keepsThemNil() {
        let item = makeItem(fatSaturated: nil, fiber: nil)
        let sut = FoodItemFormInput(item: item)
        XCTAssertNil(sut.fatSaturated)
        XCTAssertNil(sut.fiber)
    }

    func test_initFromItem_withKnownFatSaturatedAndFiber_keepsTheirValues() {
        let item = makeItem(fatSaturated: 1.5, fiber: 2.5)
        let sut = FoodItemFormInput(item: item)
        XCTAssertEqual(sut.fatSaturated, 1.5)
        XCTAssertEqual(sut.fiber, 2.5)
    }

    // MARK: - asFoodItemDomain

    func test_asFoodItemDomain_roundTripsEnglishName() {
        var sut = FoodItemFormInput(item: makeItem(engName: "Cottage cheese"))
        sut.name = "Tvaroh opraveno"
        XCTAssertEqual(sut.asFoodItemDomain().engName, "Cottage cheese", "editing an unrelated field must not blank the English name")
    }

    func test_asFoodItemDomain_whenFatSaturatedAndFiberWereUnknownAndUntouched_staysNil() {
        var sut = FoodItemFormInput(item: makeItem(fatSaturated: nil, fiber: nil))
        sut.name = "Tvaroh opraveno"
        let result = sut.asFoodItemDomain()
        XCTAssertNil(result.fatSaturated, "unknown nutrition must not silently become a false zero")
        XCTAssertNil(result.fiber, "unknown nutrition must not silently become a false zero")
    }

    func test_asFoodItemDomain_whenFatSaturatedIsExplicitlyEdited_usesTheEditedValue() {
        var sut = FoodItemFormInput(item: makeItem(fatSaturated: nil))
        sut.fatSaturated = 3
        XCTAssertEqual(sut.asFoodItemDomain().fatSaturated, 3)
    }

    // MARK: - Measure and thousands display

    func test_asFoodItemDomain_whenWeightEnteredInLitres_convertsToBaseMillilitresAndKeepsMeasure() {
        var sut = FoodItemFormInput()
        sut.measure = .millilitres
        sut.weightOfProduct = 1.5
        sut.isWeightInThousands = true
        let result = sut.asFoodItemDomain()
        XCTAssertEqual(result.weight, 1500)
        XCTAssertEqual(result.measure, .millilitres)
    }

    func test_initFromItem_withWeightAtOrAboveAThousand_displaysInThousands() {
        let sut = FoodItemFormInput(item: makeItem(weight: 1500))
        XCTAssertEqual(sut.weightOfProduct, 1.5)
        XCTAssertTrue(sut.isWeightInThousands)
    }

    func test_initFromItem_withWeightBelowAThousand_staysInBaseUnit() {
        let sut = FoodItemFormInput(item: makeItem(weight: 999))
        XCTAssertEqual(sut.weightOfProduct, 999)
        XCTAssertFalse(sut.isWeightInThousands)
    }

    // MARK: - applying(_:)

    func test_applying_readingWithMillilitres_setsMeasureAndReportsItChanged() {
        var sut = FoodItemFormInput()
        let applied = sut.applying(NutritionLabelReading(measure: .millilitres))
        XCTAssertEqual(sut.measure, .millilitres)
        XCTAssertTrue(applied.contains(.measure))
    }

    func test_applying_whenFormAlreadyAtMillilitres_isNotOverwrittenByGrams() {
        var sut = FoodItemFormInput()
        sut.measure = .millilitres
        _ = sut.applying(NutritionLabelReading(measure: .grams))
        XCTAssertEqual(sut.measure, .millilitres, "an explicitly picked measure must not be overwritten by a later reading, same as every other field")
    }

    // MARK: - Helpers

    private func makeItem(
        engName: String = "Cottage cheese",
        fatSaturated: Double? = 0.3,
        fiber: Double? = 0,
        weight: Double = 200
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: "12345678",
            kind: .catalogue,
            czName: "Tvaroh",
            engName: engName,
            weight: weight,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: fatSaturated,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: fiber,
            protein: 13,
            salt: 0.1
        )
    }
}
