//
//  FoodItemSubmissionWriter.swift
//  Kalorie
//
//  Created by Josef Antoni on 11.09.2026.
//

import Foundation

enum FoodItemSubmissionWriter {

    // MARK: - Functions

    static func write(
        id: String,
        item: FoodItemDomain,
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol
    ) async throws -> FoodItemSubmissionDomain {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        if let validationError = FoodItemValidation.validate(item) {
            throw FoodItemSubmissionError(validationError)
        }
        let existing: FoodItemDTO? = try await dataProvider.loadFromServerAsync(
            id: item.id,
            from: Constants.Firestore.foodItems
        )
        guard existing == nil else { throw FoodItemSubmissionError.itemAlreadyExists }
        let submission = FoodItemSubmissionDomain(
            id: id,
            barcode: item.id,
            submittedBy: userId,
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: item
        )
        let dto = FoodItemSubmissionDTO(
            id: submission.id,
            barcode: submission.barcode,
            submittedBy: submission.submittedBy,
            status: submission.status,
            submittedAt: submission.submittedAt,
            rejectReason: submission.rejectReason,
            item: submission.item
        )
        try await dataProvider.setAsync(dto, id: submission.id, in: Constants.Firestore.foodItemSubmissions)
        return submission
    }
}
