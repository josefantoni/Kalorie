//
//  FoodPortionValidationTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 09.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodPortionValidationTests: XCTestCase {

    // MARK: - validate(name:grams:)

    func test_validate_withEmptyName_returnsInvalidName() {
        XCTAssertEqual(FoodPortionValidation.validate(name: "", grams: 33), .invalidName)
    }

    func test_validate_withWhitespaceOnlyName_returnsInvalidName() {
        XCTAssertEqual(FoodPortionValidation.validate(name: "   ", grams: 33), .invalidName)
    }

    func test_validate_withGramsBelowOne_returnsInvalidGrams() {
        XCTAssertEqual(FoodPortionValidation.validate(name: "1 balení", grams: 0.5), .invalidGrams)
    }

    func test_validate_withValidNameAndGrams_returnsNil() {
        XCTAssertNil(FoodPortionValidation.validate(name: "1 balení", grams: 33))
    }

    // MARK: - validate(portions:)

    func test_validate_withPortionsAtLimit_returnsNil() {
        let portions = (0..<FoodPortionValidation.maxPortions).map { FoodPortionDomain(name: "Porce \($0)", grams: 10) }
        XCTAssertNil(FoodPortionValidation.validate(portions: portions))
    }

    func test_validate_withPortionsOverLimit_returnsTooMany() {
        let portions = (0..<(FoodPortionValidation.maxPortions + 1)).map { FoodPortionDomain(name: "Porce \($0)", grams: 10) }
        XCTAssertEqual(FoodPortionValidation.validate(portions: portions), .tooMany)
    }
}
