//
//  FoodItemValidation.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation
import TextKit

enum FoodItemValidationError: Error, Equatable {
    case invalidCode
    case invalidName
    case invalidCalories
    case invalidAlcoholByVolume
    case invalidPortion(FoodPortionError)
}

extension FoodItemValidationError {

    // MARK: - Functions

    func mapped<T>(
        invalidCode: T,
        invalidName: T,
        invalidCalories: T,
        invalidAlcoholByVolume: T,
        invalidPortion: (FoodPortionError) -> T
    ) -> T {
        switch self {
        case .invalidCode: return invalidCode
        case .invalidName: return invalidName
        case .invalidCalories: return invalidCalories
        case .invalidAlcoholByVolume: return invalidAlcoholByVolume
        case .invalidPortion(let portionError): return invalidPortion(portionError)
        }
    }
}

enum FoodItemRequiredNames: Equatable {
    case all
    case original(nameLanguage: String)
}

enum FoodItemValidation {

    // MARK: - Functions

    static func isValidBarcode(_ id: String) -> Bool {
        id.allSatisfy { $0.isASCII && $0.isNumber } && [8, 12, 13].contains(id.count)
    }

    static func isValidSubmissionUUID(_ id: String) -> Bool {
        UUID(uuidString: id) != nil && id == id.uppercased()
    }

    static func validate(_ item: FoodItemDomain, requiredNames: FoodItemRequiredNames) -> FoodItemValidationError? {
        guard isValidBarcode(item.id) || isValidSubmissionUUID(item.id) else { return .invalidCode }
        guard hasRequiredNames(item, requiredNames) else { return .invalidName }
        guard item.caloriesPerHundredGrams > 0 else { return .invalidCalories }
        if let alcoholByVolume = item.alcoholByVolume, !(alcoholByVolume > 0 && alcoholByVolume <= 100) {
            return .invalidAlcoholByVolume
        }
        for portion in item.portions {
            if let error = FoodPortionValidation.validate(name: portion.name, grams: portion.grams) {
                return .invalidPortion(error)
            }
        }
        if let error = FoodPortionValidation.validate(portions: item.portions) {
            return .invalidPortion(error)
        }
        return nil
    }

    // MARK: - Private

    private static func hasRequiredNames(_ item: FoodItemDomain, _ requiredNames: FoodItemRequiredNames) -> Bool {
        switch requiredNames {
        case .all:
            [item.czName, item.skName, item.engName].allSatisfy { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        case .original(let nameLanguage):
            !NameRulesKt.originalName(nameLanguage: nameLanguage, czech: item.czName, slovak: item.skName, english: item.engName).isEmpty
        }
    }
}
