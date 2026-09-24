//
//  OpenFoodFactsProductDTOTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 24.09.2026.
//

import XCTest
@testable import Kalorie

final class OpenFoodFactsProductDTOTests: XCTestCase {

    // MARK: - Tests

    func test_asDomain_matchesSharedFixtureCases() throws {
        let fixture: MappingFixture = try FixtureLoader.load("open-food-facts-mapping-cases")
        for mappingCase in fixture.cases {
            let item = mappingCase.product.asDomain()
            guard let expected = mappingCase.expected else {
                XCTAssertNil(item, mappingCase.name)
                continue
            }
            guard let item else {
                XCTFail("\(mappingCase.name): expected an item, got nil")
                continue
            }
            XCTAssertEqual(item.id, expected.id, mappingCase.name)
            XCTAssertEqual(item.czName, expected.czName, mappingCase.name)
            XCTAssertEqual(item.engName, expected.engName, mappingCase.name)
            XCTAssertEqual(item.weight, expected.weight, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.energyKJ, expected.energyKJ, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.caloriesPerHundredGrams, expected.caloriesPerHundredGrams, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.fat, expected.fat, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.fatSaturated ?? .nan, expected.fatSaturated, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.fatUnsaturatedFattyAcids, expected.fatUnsaturatedFattyAcids, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.carbohydrate, expected.carbohydrate, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.carbohydratePureSugar, expected.carbohydratePureSugar, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.fiber ?? .nan, expected.fiber, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.protein, expected.protein, accuracy: 1e-9, mappingCase.name)
            XCTAssertEqual(item.salt, expected.salt, accuracy: 1e-9, mappingCase.name)
        }
    }

    // MARK: - Helpers

    private struct MappingFixture: Decodable {
        let cases: [MappingCase]
    }

    private struct MappingCase: Decodable {
        let name: String
        let product: OpenFoodFactsProductDTO
        let expected: ExpectedItem?
    }

    private struct ExpectedItem: Decodable {
        let id: String
        let czName: String
        let engName: String
        let weight: Double
        let energyKJ: Double
        let caloriesPerHundredGrams: Double
        let fat: Double
        let fatSaturated: Double
        let fatUnsaturatedFattyAcids: Double
        let carbohydrate: Double
        let carbohydratePureSugar: Double
        let fiber: Double
        let protein: Double
        let salt: Double
    }
}
