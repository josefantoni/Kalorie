//
//  DeleteMyCreatedMealUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

protocol DeleteMyCreatedMealUseCaseProtocol {
    func callAsFunction(id: String) async throws
}

struct DeleteMyCreatedMealUseCase: DeleteMyCreatedMealUseCaseProtocol {

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
        try await dataProvider.deleteAsync(id: id, from: Constants.Firestore.myCreatedMeals(userId: userId))
    }
}

#if DEBUG
struct DeleteMyCreatedMealUseCaseFake: DeleteMyCreatedMealUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(id: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
