//
//  FoodItemSubmissionModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

enum FoodItemSubmissionStatus: String, Codable {
    case pending
    case rejected
}

struct FoodItemSubmissionDomain {

    // MARK: - Properties

    let id: String
    let barcode: String?
    let submittedBy: String
    let status: FoodItemSubmissionStatus
    let submittedAt: Date
    let rejectReason: String?
    let item: FoodItemDomain
}

enum FoodItemSubmissionError: Error {
    case invalidCode
    case invalidName
    case invalidCalories
    case invalidWeight
    case invalidPortion(FoodPortionError)
    case itemAlreadyExists

    init(_ validationError: FoodItemValidationError) {
        self = validationError.mapped(
            invalidCode: .invalidCode,
            invalidName: .invalidName,
            invalidCalories: .invalidCalories,
            invalidWeight: .invalidWeight
        ) { .invalidPortion($0) }
    }
}
