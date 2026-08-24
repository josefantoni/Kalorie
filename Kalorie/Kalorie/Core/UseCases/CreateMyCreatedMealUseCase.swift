//
//  CreateMyCreatedMealUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

protocol CreateMyCreatedMealUseCaseProtocol {
    func callAsFunction(name: String, ingredients: [MyCreatedMealIngredientDomain]) async throws -> MyCreatedMealDomain
}

struct CreateMyCreatedMealUseCase: CreateMyCreatedMealUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(name: String, ingredients: [MyCreatedMealIngredientDomain]) async throws -> MyCreatedMealDomain {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        if let error = MyCreatedMealValidation.validate(name: name, ingredients: ingredients) { throw error }
        let now = Date.now
        let meal = MyCreatedMealDomain(
            id: UUID().uuidString,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            ingredients: ingredients,
            createdAt: now,
            updatedAt: now
        )
        let dto = MyCreatedMealDTO(meal: meal)
        try await dataProvider.setAsync(dto, id: meal.id, in: Constants.Firestore.myCreatedMeals(userId: userId))
        return meal
    }
}

#if DEBUG
struct CreateMyCreatedMealUseCaseFake: CreateMyCreatedMealUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(name: String, ingredients: [MyCreatedMealIngredientDomain]) async throws -> MyCreatedMealDomain {
        if shouldThrow { throw URLError(.unknown) }
        return MyCreatedMealDomain(id: UUID().uuidString, name: name, ingredients: ingredients, createdAt: .now, updatedAt: .now)
    }
}
#endif
