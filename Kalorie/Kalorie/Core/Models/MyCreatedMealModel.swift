//
//  MyCreatedMealModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation
import MacroKit

struct MyCreatedMealIngredientDomain: Equatable {

    // MARK: - Properties

    let foodItemId: String
    let czName: String
    let engName: String
    let grams: Double
    let nutrition: FoodNutritionValues
}

struct MyCreatedMealDomain {

    // MARK: - Properties

    let id: String
    let name: String
    let ingredients: [MyCreatedMealIngredientDomain]
    let createdAt: Date
    let updatedAt: Date
}

extension MyCreatedMealDomain {

    // MARK: - Functions

    func asFoodItem() -> FoodItemDomain {
        let gramsList = ingredients.map(\.grams)
        let totalGrams = gramsList.reduce(0, +)
        func density(_ value: (FoodNutritionValues) -> Double) -> Double {
            weightedMeanPerHundredGrams(values: ingredients.map { value($0.nutrition) }, grams: gramsList)
        }
        return FoodItemDomain(
            id: id,
            kind: .createdMeal,
            czName: name,
            engName: "",
            weight: totalGrams,
            date: createdAt,
            nutrition: FoodNutritionValues(
                energyKJ: density(\.energyKJ),
                caloriesPerHundredGrams: density(\.caloriesPerHundredGrams),
                fat: density(\.fat),
                fatSaturated: density(\.fatSaturated),
                fatUnsaturatedFattyAcids: density(\.fatUnsaturatedFattyAcids),
                carbohydrate: density(\.carbohydrate),
                carbohydratePureSugar: density(\.carbohydratePureSugar),
                fiber: density(\.fiber),
                protein: density(\.protein),
                salt: density(\.salt)
            )
        )
    }
}

private func weightedMeanPerHundredGrams(values: [Double], grams: [Double]) -> Double {
    MacrosKt.weightedMeanPerHundredGrams(
        values: values.map { KotlinDouble(double: $0) },
        grams: grams.map { KotlinDouble(double: $0) }
    )
}

enum MyCreatedMealError: Error {
    case invalidName
    case noIngredients
    case invalidIngredientWeight
}

enum MyCreatedMealValidation {

    // MARK: - Functions

    static func validate(name: String, ingredients: [MyCreatedMealIngredientDomain]) -> MyCreatedMealError? {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty { return .invalidName }
        if ingredients.isEmpty { return .noIngredients }
        if ingredients.contains(where: { $0.grams < 1 }) { return .invalidIngredientWeight }
        return nil
    }

    static func canSave(name: String, ingredients: [MyCreatedMealIngredientDomain]) -> Bool {
        validate(name: name, ingredients: ingredients) == nil
    }
}
