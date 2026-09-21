//
//  FoodConsumedDomainTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodConsumedDomainTests: XCTestCase {

    // MARK: - Tests

    func test_copyWithWeight_preservesMillilitresMeasure() {
        var food = makeFood()
        food.measure = .millilitres
        let copy = food.copy(weight: 300)
        XCTAssertEqual(copy.measure, .millilitres, "an edited milk entry must not turn into grams")
        XCTAssertEqual(copy.weight, 300)
    }

    // MARK: - Helpers

    private func makeFood() -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: "1",
            foodItemId: "12345",
            foodItemKind: .catalogue,
            czName: "Mléko",
            engName: "Milk",
            weight: 200,
            date: .now,
            calories: 130,
            caloriesPerHundredGrams: 65,
            energyKJ: 270,
            protein: 3.2,
            carbohydrate: 4.8,
            carbohydrateSugar: 4.8,
            fat: 3.5,
            fatSaturated: 2.1,
            fatUnsaturated: 1,
            fiber: 0,
            salt: 0.1,
            mealTypeId: nil
        )
    }
}
