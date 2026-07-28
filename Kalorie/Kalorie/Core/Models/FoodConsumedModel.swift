//
//  FoodConsumedDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct FoodConsumedDomain: BilingualNamed {

    // MARK: - Properties

    let id: String
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
