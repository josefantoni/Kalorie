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
    let defaultKey: String?

    // MARK: - Init

    init(id: String, name: String, startMinutes: Int, endMinutes: Int, defaultKey: String? = nil) {
        self.id = id
        self.name = name
        self.startMinutes = startMinutes
        self.endMinutes = endMinutes
        self.defaultKey = defaultKey
    }

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, name, startMinutes, endMinutes, defaultKey
    }
}
