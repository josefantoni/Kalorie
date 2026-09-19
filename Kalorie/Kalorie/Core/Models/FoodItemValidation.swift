//
//  FoodItemValidation.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

enum FoodItemValidationError: Error {
    case invalidCode
    case invalidName
    case invalidCalories
    case invalidWeight
    case invalidPortion(FoodPortionError)
}

extension FoodItemValidationError {

    // MARK: - Functions

    func mapped<T>(
        invalidCode: T,
        invalidName: T,
        invalidCalories: T,
        invalidWeight: T,
        invalidPortion: (FoodPortionError) -> T
    ) -> T {
        switch self {
        case .invalidCode: return invalidCode
        case .invalidName: return invalidName
        case .invalidCalories: return invalidCalories
        case .invalidWeight: return invalidWeight
        case .invalidPortion(let portionError): return invalidPortion(portionError)
        }
    }
}

enum FoodItemValidation {

    // MARK: - Functions

    static func isValidBarcode(_ id: String) -> Bool {
        id.allSatisfy { $0.isASCII && $0.isNumber } && [8, 12, 13].contains(id.count)
    }

    static func isValidSubmissionUUID(_ id: String) -> Bool {
        UUID(uuidString: id) != nil && id == id.uppercased()
    }

    static func validate(_ item: FoodItemDomain) -> FoodItemValidationError? {
        guard isValidBarcode(item.id) || isValidSubmissionUUID(item.id) else { return .invalidCode }
        guard !item.czName.isEmpty else { return .invalidName }
        guard item.caloriesPerHundredGrams > 0 else { return .invalidCalories }
        guard item.weight > 0 else { return .invalidWeight }
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
}
