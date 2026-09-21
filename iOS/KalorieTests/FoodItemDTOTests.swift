//
//  FoodItemDTOTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodItemDTOTests: XCTestCase {

    // MARK: - Tests

    func test_asDomain_whenMeasureUnitIsAbsent_defaultsToGrams() throws {
        var json = try encodedJSON()
        json.removeValue(forKey: "measure_unit")
        let dto = try decode(json)
        XCTAssertEqual(dto.asDomain().measure, .grams, "every catalogue item written before this design must stay grams")
    }

    func test_asDomain_whenMeasureUnitIsUnknown_defaultsToGramsWithoutThrowing() throws {
        var json = try encodedJSON()
        json["measure_unit"] = "litres"
        let dto = try decode(json)
        XCTAssertEqual(dto.asDomain().measure, .grams, "one bad document must not fail a whole search")
    }

    func test_asDomain_whenMeasureUnitIsMillilitres_preservesIt() throws {
        var json = try encodedJSON()
        json["measure_unit"] = "millilitres"
        let dto = try decode(json)
        XCTAssertEqual(dto.asDomain().measure, .millilitres)
    }

    // MARK: - Helpers

    private func encodedJSON() throws -> [String: Any] {
        try JSONSerialization.jsonObject(with: JSONEncoder().encode(FoodItemDTO(item: makeItem()))) as? [String: Any] ?? [:]
    }

    private func decode(_ json: [String: Any]) throws -> FoodItemDTO {
        try JSONDecoder().decode(FoodItemDTO.self, from: JSONSerialization.data(withJSONObject: json))
    }

    private func makeItem() -> FoodItemDomain {
        FoodItemDomain(
            id: "12345678",
            kind: .catalogue,
            czName: "Mléko",
            engName: "Milk",
            weight: 1000,
            date: .now,
            energyKJ: 270,
            caloriesPerHundredGrams: 64,
            fat: 3.5,
            fatSaturated: 2.1,
            fatUnsaturatedFattyAcids: 1,
            carbohydrate: 4.8,
            carbohydratePureSugar: 4.8,
            fiber: 0,
            protein: 3.2,
            salt: 0.1
        )
    }
}
