//
//  BodyWeightDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

struct BodyWeightDTO: Codable {

    // MARK: - Properties

    let id: String
    let weightKg: Double

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id
        case weightKg = "weight_kg"
    }

    // MARK: - Init

    init(weight: BodyWeightDomain) {
        id = weight.dayKey
        weightKg = weight.kilograms
    }

    // MARK: - Functions

    func asDomain() -> BodyWeightDomain {
        BodyWeightDomain(dayKey: id, kilograms: weightKg)
    }
}
