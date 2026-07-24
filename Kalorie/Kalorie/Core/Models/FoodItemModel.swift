//
//  FoodItemDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct FoodItemDomain: BilingualNamed {

    // MARK: - Properties

    let id: String
    let czName: String
    let engName: String
    let weight: Double
    let date: Date
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
