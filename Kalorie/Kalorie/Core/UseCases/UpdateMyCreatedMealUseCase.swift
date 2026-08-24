//
//  UpdateMyCreatedMealUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

protocol UpdateMyCreatedMealUseCaseProtocol {
    func callAsFunction(_ meal: MyCreatedMealDomain) async throws
}

struct UpdateMyCreatedMealUseCase: UpdateMyCreatedMealUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ meal: MyCreatedMealDomain) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        if let error = MyCreatedMealValidation.validate(name: meal.name, ingredients: meal.ingredients) { throw error }
        let updated = MyCreatedMealDomain(
            id: meal.id,
            name: meal.name.trimmingCharacters(in: .whitespacesAndNewlines),
            ingredients: meal.ingredients,
            createdAt: meal.createdAt,
            updatedAt: .now
        )
        let dto = MyCreatedMealDTO(meal: updated)
        try await dataProvider.setAsync(dto, id: updated.id, in: Constants.Firestore.myCreatedMeals(userId: userId))
    }
}

#if DEBUG
struct UpdateMyCreatedMealUseCaseFake: UpdateMyCreatedMealUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ meal: MyCreatedMealDomain) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
