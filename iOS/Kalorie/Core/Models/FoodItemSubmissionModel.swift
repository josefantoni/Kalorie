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
    case invalidAlcoholByVolume
    case invalidPortion(FoodPortionError)
    case itemAlreadyExists
    case photoMissing
    case photoUploadFailed

    init(_ validationError: FoodItemValidationError) {
        self = validationError.mapped(
            invalidCode: .invalidCode,
            invalidName: .invalidName,
            invalidCalories: .invalidCalories,
            invalidAlcoholByVolume: .invalidAlcoholByVolume
        ) { .invalidPortion($0) }
    }
}
