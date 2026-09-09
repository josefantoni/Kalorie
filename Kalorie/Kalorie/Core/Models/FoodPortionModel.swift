//
//  FoodPortionModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import Foundation

struct FoodPortionDomain: Hashable {

    // MARK: - Properties

    let name: String
    let grams: Double
}

enum FoodPortionError: Error {
    case invalidName
    case invalidGrams
    case tooMany
}

enum FoodPortionValidation {

    // MARK: - Properties

    static let maxPortions = 20

    // MARK: - Functions

    static func validate(name: String, grams: Double) -> FoodPortionError? {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return .invalidName }
        guard grams >= 1 else { return .invalidGrams }
        return nil
    }

    static func validate(portions: [FoodPortionDomain]) -> FoodPortionError? {
        portions.count > maxPortions ? .tooMany : nil
    }
}
