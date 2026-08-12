//
//  RemoveFavouriteFoodUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import Foundation

protocol RemoveFavouriteFoodUseCaseProtocol {
    func callAsFunction(id: String) async throws
}

struct RemoveFavouriteFoodUseCase: RemoveFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(id: String) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        try await dataProvider.deleteAsync(id: id, from: Constants.Firestore.favouriteFoods(userId: userId))
    }
}

#if DEBUG
struct RemoveFavouriteFoodUseCaseFake: RemoveFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(id: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
