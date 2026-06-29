//
//  FoodConsumedDomain.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

struct FoodConsumedDomain {

    // MARK: - Properties

    let id: String
    let name: String
    let weight: Double
    let date: Date
    let calories: Int
    let mealTypeId: Int?
}
