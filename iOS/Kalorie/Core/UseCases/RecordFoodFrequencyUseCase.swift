//
//  RecordFoodFrequencyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.10.2026.
//

import Foundation

protocol RecordFoodFrequencyUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain, date: Date) async throws
}

struct RecordFoodFrequencyUseCase: RecordFoodFrequencyUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, date: Date) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        try await dataProvider.incrementEntryAsync(
            FoodFrequencyItemDTO(item: item),
            entryId: item.id,
            lastLoggedAt: date.timeIntervalSince1970,
            documentId: Constants.Firestore.foodFrequencyDocumentId,
            in: Constants.Firestore.stats(userId: userId)
        )
    }
}

#if DEBUG
struct RecordFoodFrequencyUseCaseFake: RecordFoodFrequencyUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, date: Date) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
