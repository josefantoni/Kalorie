//
//  FoodItemDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

enum FoodItemKind: String, Codable {
    case catalogue
    case external
    case createdMeal = "created_meal"
}

struct FoodItemDomain: BilingualNamed {

    // MARK: - Properties

    let id: String
    let kind: FoodItemKind
    let czName: String
    let engName: String
    let weight: Double
    let date: Date
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

extension FoodItemDomain {

    // MARK: - Properties

    var nutrition: FoodNutritionValues {
        FoodNutritionValues(
            energyKJ: energyKJ,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: fat,
            fatSaturated: fatSaturated,
            fatUnsaturatedFattyAcids: fatUnsaturatedFattyAcids,
            carbohydrate: carbohydrate,
            carbohydratePureSugar: carbohydratePureSugar,
            fiber: fiber,
            protein: protein,
            salt: salt
        )
    }

    // MARK: - Init

    init(
        id: String,
        kind: FoodItemKind,
        czName: String,
        engName: String,
        weight: Double,
        date: Date,
        nutrition: FoodNutritionValues
    ) {
        self.init(
            id: id,
            kind: kind,
            czName: czName,
            engName: engName,
            weight: weight,
            date: date,
            energyKJ: nutrition.energyKJ,
            caloriesPerHundredGrams: nutrition.caloriesPerHundredGrams,
            fat: nutrition.fat,
            fatSaturated: nutrition.fatSaturated,
            fatUnsaturatedFattyAcids: nutrition.fatUnsaturatedFattyAcids,
            carbohydrate: nutrition.carbohydrate,
            carbohydratePureSugar: nutrition.carbohydratePureSugar,
            fiber: nutrition.fiber,
            protein: nutrition.protein,
            salt: nutrition.salt
        )
    }
}
