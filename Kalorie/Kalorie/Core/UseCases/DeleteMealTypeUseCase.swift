//
//  DeleteMealTypeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

protocol DeleteMealTypeUseCaseProtocol {
    func callAsFunction(_ mealType: MealTypeDomain) async throws
}

struct DeleteMealTypeUseCase: DeleteMealTypeUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ mealType: MealTypeDomain) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        try await dataProvider.deleteAsync(id: mealType.id, from: Constants.Firestore.mealTypes(userId: userId))
    }
}

#if DEBUG
struct DeleteMealTypeUseCaseFake: DeleteMealTypeUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ mealType: MealTypeDomain) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
