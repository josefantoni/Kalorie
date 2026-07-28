//
//  FoodConsumedDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

struct FoodConsumedDTO: Codable {

    // MARK: - Properties

    let id: String
    let czName: String
    let engName: String
    let weight: Double
    let date: TimeInterval
    let calories: Int
    let protein: Double
    let carbohydrate: Double
    let carbohydrateSugar: Double
    let fat: Double
    let fatUnsaturated: Double
    let fiber: Double
    let salt: Double

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, weight, date, calories, protein, carbohydrate, fat, fiber, salt
        case czName = "cz_name"
        case engName = "eng_name"
        case carbohydrateSugar = "carbohydrate_sugar"
        case fatUnsaturated = "fat_unsaturated"
    }
}
