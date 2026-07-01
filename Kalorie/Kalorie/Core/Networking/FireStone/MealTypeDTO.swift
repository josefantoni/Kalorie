//
//  MealTypeDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

struct MealTypeDTO: Codable {

    // MARK: - Properties

    let id: Int
    let name: String
    let startTime: TimeInterval
    let endTime: TimeInterval
}
