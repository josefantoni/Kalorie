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

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, weight, date, calories
        case czName = "cz_name"
        case engName = "eng_name"
    }
}
