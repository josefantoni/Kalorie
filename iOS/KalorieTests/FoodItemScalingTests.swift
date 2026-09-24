//
//  FoodItemScalingTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 24.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodItemScalingTests: XCTestCase {

    // MARK: - Tests

    func test_scaled_matchesSharedFixtureCases() throws {
        let fixture: ScalingFixture = try FixtureLoader.load("food-item-scaling-cases")
        for scalingCase in fixture.cases {
            let scaled = makeItem(scalingCase.item).scaled(toGrams: scalingCase.grams)
            let expected = scalingCase.expected
            XCTAssertEqual(scaled.calories, expected.calories, scalingCase.name)
            XCTAssertEqual(scaled.energyKJ, expected.energyKJ, accuracy: 1e-9, scalingCase.name)
            XCTAssertEqual(scaled.protein, expected.protein, accuracy: 1e-9, scalingCase.name)
            XCTAssertEqual(scaled.carbohydrate, expected.carbohydrate, accuracy: 1e-9, scalingCase.name)
            XCTAssertEqual(scaled.carbohydrateSugar, expected.carbohydrateSugar, accuracy: 1e-9, scalingCase.name)
            XCTAssertEqual(scaled.fat, expected.fat, accuracy: 1e-9, scalingCase.name)
            XCTAssertEqual(scaled.fatUnsaturated, expected.fatUnsaturated, accuracy: 1e-9, scalingCase.name)
            XCTAssertEqual(scaled.salt, expected.salt, accuracy: 1e-9, scalingCase.name)
            assertEqual(scaled.fatSaturated, expected.fatSaturated, scalingCase.name)
            assertEqual(scaled.fiber, expected.fiber, scalingCase.name)
        }
    }

    // MARK: - Helpers

    private func assertEqual(_ actual: Double?, _ expected: Double?, _ name: String) {
        switch (actual, expected) {
        case (nil, nil):
            break
        case (let actual?, let expected?):
            XCTAssertEqual(actual, expected, accuracy: 1e-9, name)
        default:
            XCTFail("\(name): expected \(String(describing: expected)), got \(String(describing: actual))")
        }
    }

    private func makeItem(_ fields: ItemFields) -> FoodItemDomain {
        FoodItemDomain(
            id: "12345678",
            kind: .catalogue,
            czName: "Tvaroh",
            engName: "Cottage cheese",
            weight: 200,
            date: .now,
            energyKJ: fields.energyKJ,
            caloriesPerHundredGrams: fields.caloriesPerHundredGrams,
            fat: fields.fat,
            fatSaturated: fields.fatSaturated,
            fatUnsaturatedFattyAcids: fields.fatUnsaturatedFattyAcids,
            carbohydrate: fields.carbohydrate,
            carbohydratePureSugar: fields.carbohydratePureSugar,
            fiber: fields.fiber,
            protein: fields.protein,
            salt: fields.salt
        )
    }

    private struct ScalingFixture: Decodable {
        let cases: [ScalingCase]
    }

    private struct ScalingCase: Decodable {
        let name: String
        let item: ItemFields
        let grams: Double
        let expected: ExpectedMacros
    }

    private struct ItemFields: Decodable {
        let energyKJ: Double
        let caloriesPerHundredGrams: Double
        let fat: Double
        let fatSaturated: Double?
        let fatUnsaturatedFattyAcids: Double
        let carbohydrate: Double
        let carbohydratePureSugar: Double
        let fiber: Double?
        let protein: Double
        let salt: Double
    }

    private struct ExpectedMacros: Decodable {
        let calories: Int
        let energyKJ: Double
        let protein: Double
        let carbohydrate: Double
        let carbohydrateSugar: Double
        let fat: Double
        let fatSaturated: Double?
        let fatUnsaturated: Double
        let fiber: Double?
        let salt: Double
    }
}
