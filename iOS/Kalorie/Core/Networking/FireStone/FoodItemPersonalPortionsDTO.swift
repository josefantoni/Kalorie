//
//  FoodItemPersonalPortionsDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import Foundation

struct FoodItemPersonalPortionsDTO: Codable {

    // MARK: - Properties

    let id: String
    let portions: [FoodPortionDTO]
}
