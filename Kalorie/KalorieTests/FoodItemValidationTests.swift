//
//  FoodItemValidationTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodItemValidationTests: XCTestCase {

    // MARK: - Tests

    func test_validate_withInvalidCode_returnsInvalidCode() {
        XCTAssertEqual(FoodItemValidation.validate(makeItem(id: "123456789")), .invalidCode)
    }

    func test_validate_withNonASCIIDigitsCode_returnsInvalidCode() {
        XCTAssertEqual(FoodItemValidation.validate(makeItem(id: "١٢٣٤٥٦٧٨")), .invalidCode)
    }

    func test_validate_withEmptyName_returnsInvalidName() {
        XCTAssertEqual(FoodItemValidation.validate(makeItem(name: "")), .invalidName)
    }

    func test_validate_withZeroCalories_returnsInvalidCalories() {
        XCTAssertEqual(FoodItemValidation.validate(makeItem(caloriesPerHundredGrams: 0)), .invalidCalories)
    }

    func test_validate_withZeroWeight_returnsInvalidWeight() {
        XCTAssertEqual(FoodItemValidation.validate(makeItem(weight: 0)), .invalidWeight)
    }

    func test_validate_withInvalidPortion_returnsInvalidPortion() {
        let item = makeItem(portions: [FoodPortionDomain(name: "", grams: 30)])
        XCTAssertEqual(FoodItemValidation.validate(item), .invalidPortion(.invalidName))
    }

    func test_validate_withValidItem_returnsNil() {
        XCTAssertNil(FoodItemValidation.validate(makeItem()))
    }

    // MARK: - Helpers

    private func makeItem(
        id: String = "12345678",
        name: String = "Tvaroh",
        weight: Double = 200,
        caloriesPerHundredGrams: Double = 80,
        portions: [FoodPortionDomain] = []
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: name,
            engName: "Cottage cheese",
            weight: weight,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: 0.5,
            fatSaturated: 0.3,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1,
            portions: portions
        )
    }
}

extension FoodItemValidationError: Equatable {
    public static func == (lhs: FoodItemValidationError, rhs: FoodItemValidationError) -> Bool {
        switch (lhs, rhs) {
        case (.invalidCode, .invalidCode), (.invalidName, .invalidName), (.invalidCalories, .invalidCalories), (.invalidWeight, .invalidWeight):
            return true
        case (.invalidPortion(let lhsError), .invalidPortion(let rhsError)):
            return lhsError == rhsError
        default:
            return false
        }
    }
}
