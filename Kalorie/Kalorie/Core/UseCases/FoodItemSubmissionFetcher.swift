//
//  FoodItemSubmissionFetcher.swift
//  Kalorie
//
//  Created by Josef Antoni on 11.09.2026.
//

import Foundation

enum FoodItemSubmissionFetcher {

    // MARK: - Functions

    static func fetch(
        where field: String,
        isEqualTo value: String,
        dataProvider: any FirestoreDataProviderProtocol
    ) async throws -> [FoodItemSubmissionDomain] {
        let dtos: [FoodItemSubmissionDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItemSubmissions,
            where: field,
            isEqualTo: value,
            orderBy: "submitted_at",
            descending: true
        )
        return dtos.map { $0.asDomain() }
    }
}
