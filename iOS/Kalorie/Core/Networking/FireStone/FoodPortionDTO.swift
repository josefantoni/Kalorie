//
//  FoodPortionDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import Foundation

struct FoodPortionDTO: Codable {

    // MARK: - Properties

    let name: String
    let grams: Double

    // MARK: - Init

    init(portion: FoodPortionDomain) {
        name = portion.name
        grams = portion.grams
    }

    // MARK: - Functions

    func asDomain() -> FoodPortionDomain {
        FoodPortionDomain(name: name, grams: grams)
    }
}
