//
//  ConfirmMealTypesEmptyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation

protocol ConfirmMealTypesEmptyUseCaseProtocol {
    func callAsFunction() async throws -> Bool
}

struct ConfirmMealTypesEmptyUseCase: ConfirmMealTypesEmptyUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos: [MealTypeDTO] = try await dataProvider.loadFromServerAsync(from: Constants.Firestore.mealTypes(userId: userId))
        return dtos.isEmpty
    }
}

#if DEBUG
struct ConfirmMealTypesEmptyUseCaseFake: ConfirmMealTypesEmptyUseCaseProtocol {

    // MARK: - Properties

    var stubbedResult = false
    var stubbedError: Error?

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        if let stubbedError { throw stubbedError }
        return stubbedResult
    }
}
#endif
