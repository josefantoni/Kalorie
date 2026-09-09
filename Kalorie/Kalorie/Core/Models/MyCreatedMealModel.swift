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
    let portions: [FoodPortionDomain]

    // MARK: - Init

    init(
        id: String,
        name: String,
        ingredients: [MyCreatedMealIngredientDomain],
        createdAt: Date,
        updatedAt: Date,
        portions: [FoodPortionDomain] = []
    ) {
        self.id = id
        self.name = name
        self.ingredients = ingredients
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.portions = portions
    }
}

extension MyCreatedMealDomain {

    // MARK: - Functions

    func asFoodItem() -> FoodItemDomain {
        let gramsList = ingredients.map(\.grams)
        let totalGrams = gramsList.reduce(0, +)
        func density(_ value: (FoodNutritionValues) -> Double) -> Double {
            weightedMeanPerHundredGrams(values: ingredients.map { value($0.nutrition) }, grams: gramsList)
        }
        func densityOptional(_ value: (FoodNutritionValues) -> Double?) -> Double? {
            var values: [Double] = []
            for ingredient in ingredients {
                guard let resolved = value(ingredient.nutrition) else { return nil }
                values.append(resolved)
            }
            return weightedMeanPerHundredGrams(values: values, grams: gramsList)
        }
        return FoodItemDomain(
            id: id,
            kind: .createdMeal,
            czName: name,
            engName: "",
            weight: totalGrams,
            date: createdAt,
            energyKJ: density(\.energyKJ),
            caloriesPerHundredGrams: density(\.caloriesPerHundredGrams),
            fat: density(\.fat),
            fatSaturated: densityOptional(\.fatSaturated),
            fatUnsaturatedFattyAcids: density(\.fatUnsaturatedFattyAcids),
            carbohydrate: density(\.carbohydrate),
            carbohydratePureSugar: density(\.carbohydratePureSugar),
            fiber: densityOptional(\.fiber),
            protein: density(\.protein),
            salt: density(\.salt),
            portions: portions
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
    case invalidPortion
}

enum MyCreatedMealValidation {

    // MARK: - Functions

    static func validate(
        name: String,
        ingredients: [MyCreatedMealIngredientDomain],
        portions: [FoodPortionDomain] = []
    ) -> MyCreatedMealError? {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty { return .invalidName }
        if ingredients.isEmpty { return .noIngredients }
        if ingredients.contains(where: { $0.grams < 1 }) { return .invalidIngredientWeight }
        if portions.contains(where: { FoodPortionValidation.validate(name: $0.name, grams: $0.grams) != nil }) { return .invalidPortion }
        if FoodPortionValidation.validate(portions: portions) != nil { return .invalidPortion }
        return nil
    }

    static func canSave(
        name: String,
        ingredients: [MyCreatedMealIngredientDomain],
        portions: [FoodPortionDomain] = []
    ) -> Bool {
        validate(name: name, ingredients: ingredients, portions: portions) == nil
    }
}
