//
//  DeleteMySubmissionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.09.2026.
//

import Foundation

protocol DeleteMySubmissionUseCaseProtocol {
    func callAsFunction(id: String) async throws
}

struct DeleteMySubmissionUseCase: DeleteMySubmissionUseCaseProtocol {

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
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        try await dataProvider.deleteAsync(id: id, from: Constants.Firestore.foodItemSubmissions)
    }
}

#if DEBUG
struct DeleteMySubmissionUseCaseFake: DeleteMySubmissionUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(id: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
