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
    let caloriesPerHundredGrams: Double
    let energyKJ: Double
    let protein: Double
    let carbohydrate: Double
    let carbohydrateSugar: Double
    let fat: Double
    let fatSaturated: Double?
    let fatUnsaturated: Double
    let fiber: Double?
    let salt: Double
    let mealTypeId: String?

    func copy(
        weight: Double? = nil,
        calories: Int? = nil,
        caloriesPerHundredGrams: Double? = nil,
        energyKJ: Double? = nil,
        protein: Double? = nil,
        carbohydrate: Double? = nil,
        carbohydrateSugar: Double? = nil,
        fat: Double? = nil,
        fatSaturated: Double?? = nil,
        fatUnsaturated: Double? = nil,
        fiber: Double?? = nil,
        salt: Double? = nil,
        mealTypeId: String?? = nil
    ) -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: id,
            foodItemId: foodItemId,
            foodItemKind: foodItemKind,
            czName: czName,
            engName: engName,
            weight: weight ?? self.weight,
            date: date,
            calories: calories ?? self.calories,
            caloriesPerHundredGrams: caloriesPerHundredGrams ?? self.caloriesPerHundredGrams,
            energyKJ: energyKJ ?? self.energyKJ,
            protein: protein ?? self.protein,
            carbohydrate: carbohydrate ?? self.carbohydrate,
            carbohydrateSugar: carbohydrateSugar ?? self.carbohydrateSugar,
            fat: fat ?? self.fat,
            fatSaturated: fatSaturated ?? self.fatSaturated,
            fatUnsaturated: fatUnsaturated ?? self.fatUnsaturated,
            fiber: fiber ?? self.fiber,
            salt: salt ?? self.salt,
            mealTypeId: mealTypeId ?? self.mealTypeId
        )
    }
}

struct ScaledMacros {
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

    private init(calories: Int, scaled: Macros, energyKJ: Double, fatSaturated: Double?, fiber: Double?) {
        self.calories = calories
        self.energyKJ = energyKJ
        protein = scaled.protein
        carbohydrate = scaled.carbohydrate
        carbohydrateSugar = scaled.carbohydrateSugar
        fat = scaled.fat
        self.fatSaturated = fatSaturated
        fatUnsaturated = scaled.fatUnsaturated
        self.fiber = fiber
        salt = scaled.salt
    }

    init(food: FoodConsumedDomain, newWeight: Double) {
        // calories is rescaled from the entry's own stored per-100g basis, not from its already-
        // rounded absolute value — see ADR 0016, which is what removes the compounding rounding
        // error an edit-after-edit would otherwise accumulate.
        let ratio = food.weight > 0 ? newWeight / food.weight : 1
        let calories = MacrosKt.scaledCalories(caloriesPerHundredGrams: food.caloriesPerHundredGrams, ratio: newWeight / 100)
        let scaled = Macros(
            calories: 0,
            protein: food.protein,
            carbohydrate: food.carbohydrate,
            carbohydrateSugar: food.carbohydrateSugar,
            fat: food.fat,
            fatUnsaturated: food.fatUnsaturated,
            fiber: food.fiber ?? 0,
            salt: food.salt
        ).scaled(factor: ratio)
        self.init(
            calories: Int(calories),
            scaled: scaled,
            energyKJ: food.energyKJ * ratio,
            fatSaturated: food.fatSaturated.map { $0 * ratio },
            fiber: food.fiber.map { $0 * ratio }
        )
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
            fiber: item.fiber ?? 0,
            salt: item.salt
        ).scaled(factor: ratio)
        self.init(
            calories: Int(calories),
            scaled: scaled,
            energyKJ: item.energyKJ * ratio,
            fatSaturated: item.fatSaturated.map { $0 * ratio },
            fiber: item.fiber.map { $0 * ratio }
        )
    }
}

extension FoodItemDomain {
    func scaled(toGrams grams: Double) -> ScaledMacros {
        ScaledMacros(item: self, ratio: grams / 100)
    }
}
