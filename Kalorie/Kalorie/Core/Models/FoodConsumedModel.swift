//
//  FoodConsumedDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import MacroKit

struct FoodConsumedDomain: BilingualNamed, Hashable {

    // MARK: - Properties

    let id: String
    let foodItemId: String
    let foodItemKind: FoodItemKind
    let czName: String
    let engName: String
    let weight: Double
    let date: Date
    let calories: Int
    let protein: Double
    let carbohydrate: Double
    let carbohydrateSugar: Double
    let fat: Double
    let fatUnsaturated: Double
    let fiber: Double
    let salt: Double
}

struct ScaledMacros {
    let calories: Int
    let protein: Double
    let carbohydrate: Double
    let carbohydrateSugar: Double
    let fat: Double
    let fatUnsaturated: Double
    let fiber: Double
    let salt: Double

    init(food: FoodConsumedDomain, ratio: Double) {
        let scaled = Macros(
            calories: Int32(food.calories),
            protein: food.protein,
            carbohydrate: food.carbohydrate,
            carbohydrateSugar: food.carbohydrateSugar,
            fat: food.fat,
            fatUnsaturated: food.fatUnsaturated,
            fiber: food.fiber,
            salt: food.salt
        ).scaled(factor: ratio)
        calories = Int(scaled.calories)
        protein = scaled.protein
        carbohydrate = scaled.carbohydrate
        carbohydrateSugar = scaled.carbohydrateSugar
        fat = scaled.fat
        fatUnsaturated = scaled.fatUnsaturated
        fiber = scaled.fiber
        salt = scaled.salt
    }
}
