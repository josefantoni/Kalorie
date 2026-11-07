//
//  FetchHasPendingNotificationsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

protocol FetchHasPendingNotificationsUseCaseProtocol {
    func callAsFunction() async -> Bool
}

struct FetchHasPendingNotificationsUseCase: FetchHasPendingNotificationsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction() async -> Bool {
        do {
            async let pendingSubmissions = dataProvider.countAsync(
                from: Constants.Firestore.foodItemSubmissions,
                where: Constants.Firestore.Field.status,
                isEqualTo: FoodItemSubmissionStatus.pending.rawValue
            )
            async let reports = dataProvider.countAsync(from: Constants.Firestore.foodItemReports)
            async let photoSuggestions = dataProvider.countAsync(from: Constants.Firestore.photoSuggestions)
            let counts = try await [pendingSubmissions, reports, photoSuggestions]
            return counts.contains { $0 > 0 }
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
            return false
        }
    }
}

#if DEBUG
struct FetchHasPendingNotificationsUseCaseFake: FetchHasPendingNotificationsUseCaseProtocol {

    // MARK: - Properties

    var hasPending = false

    // MARK: - Functions

    func callAsFunction() async -> Bool { hasPending }
}
#endif
