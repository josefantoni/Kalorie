//
//  FoodConsumedDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct FoodConsumedDomain: BilingualNamed, Hashable {

    // MARK: - Properties

    let id: String
    let foodItemId: String
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
        calories = Int((Double(food.calories) * ratio).rounded())
        protein = food.protein * ratio
        carbohydrate = food.carbohydrate * ratio
        carbohydrateSugar = food.carbohydrateSugar * ratio
        fat = food.fat * ratio
        fatUnsaturated = food.fatUnsaturated * ratio
        fiber = food.fiber * ratio
        salt = food.salt * ratio
    }
}
