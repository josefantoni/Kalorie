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
    let name: String
    let weight: Double
    let date: TimeInterval
    let calories: Int
}
