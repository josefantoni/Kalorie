//
//  UpdateFoodConsumedUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.07.2026.
//

import Foundation

enum UpdateFoodConsumedError: Error {
    case invalidWeight
}

protocol UpdateFoodConsumedUseCaseProtocol {
    func callAsFunction(_ food: FoodConsumedDomain, newWeight: Double) async throws
}

struct UpdateFoodConsumedUseCase: UpdateFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ food: FoodConsumedDomain, newWeight: Double) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        guard food.weight > 0 else { throw UpdateFoodConsumedError.invalidWeight }
        let scaled = ScaledMacros(food: food, newWeight: newWeight)
        let dto = FoodConsumedDTO(
            id: food.id,
            foodItemId: food.foodItemId,
            foodItemKind: food.foodItemKind,
            czName: food.czName,
            engName: food.engName,
            weight: newWeight,
            date: food.date.timeIntervalSince1970,
            calories: scaled.calories,
            caloriesPerHundredGrams: food.caloriesPerHundredGrams,
            protein: scaled.protein,
            carbohydrate: scaled.carbohydrate,
            carbohydrateSugar: scaled.carbohydrateSugar,
            fat: scaled.fat,
            fatUnsaturated: scaled.fatUnsaturated,
            fiber: scaled.fiber,
            salt: scaled.salt
        )
        try await dataProvider.setAsync(dto, id: food.id, in: Constants.Firestore.foodConsumed(userId: userId))
    }
}

#if DEBUG
struct UpdateFoodConsumedUseCaseFake: UpdateFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ food: FoodConsumedDomain, newWeight: Double) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
