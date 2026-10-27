//
//  DeleteBodyWeightUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

protocol DeleteBodyWeightUseCaseProtocol {
    func callAsFunction(dayKey: String) async throws
}

struct DeleteBodyWeightUseCase: DeleteBodyWeightUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(dayKey: String) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        try await dataProvider.deleteAsync(id: dayKey, from: Constants.Firestore.bodyWeights(userId: userId))
    }
}

#if DEBUG
struct DeleteBodyWeightUseCaseFake: DeleteBodyWeightUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(dayKey: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
