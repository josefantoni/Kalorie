//
//  MealTypeDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

struct MealTypeDTO: Codable {

    // MARK: - Properties

    let id: String
    let name: String
    let startMinutes: Int
    let endMinutes: Int

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, name, startMinutes, endMinutes
    }
}
