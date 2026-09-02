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

    init(item: FoodItemDomain, ratio: Double) {
        // calories is scaled separately below: caloriesPerHundredGrams is fractional, and rounding
        // it here before .scaled() would round twice instead of once.
        let calories = MacrosKt.scaledCalories(caloriesPerHundredGrams: item.caloriesPerHundredGrams, ratio: ratio)
        let scaled = Macros(
            calories: 0,
            protein: item.protein,
            carbohydrate: item.carbohydrate,
            carbohydrateSugar: item.carbohydratePureSugar,
            fat: item.fat,
            fatUnsaturated: item.fatUnsaturatedFattyAcids,
            fiber: item.fiber,
            salt: item.salt
        ).scaled(factor: ratio)
        self.calories = Int(calories)
        protein = scaled.protein
        carbohydrate = scaled.carbohydrate
        carbohydrateSugar = scaled.carbohydrateSugar
        fat = scaled.fat
        fatUnsaturated = scaled.fatUnsaturated
        fiber = scaled.fiber
        salt = scaled.salt
    }
}

extension FoodItemDomain {
    func scaled(toGrams grams: Double) -> ScaledMacros {
        ScaledMacros(item: self, ratio: grams / 100)
    }
}
