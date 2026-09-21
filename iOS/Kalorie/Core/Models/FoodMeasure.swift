//
//  FoodMeasure.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.09.2026.
//

import Foundation

enum FoodMeasure: String, Codable, Hashable {
    case grams
    case millilitres
}

extension FoodMeasure {

    // MARK: - Properties

    var unitSymbol: String {
        switch self {
        case .grams: L10n.Common.unitGrams
        case .millilitres: L10n.Common.unitMillilitres
        }
    }

    var thousandUnitSymbol: String {
        switch self {
        case .grams: L10n.Common.unitKilograms
        case .millilitres: L10n.Common.unitLitres
        }
    }
}
