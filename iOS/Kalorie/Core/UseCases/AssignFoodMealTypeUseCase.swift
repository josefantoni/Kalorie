//
//  AssignFoodMealTypeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 03.09.2026.
//

import Foundation

protocol AssignFoodMealTypeUseCaseProtocol {
    func callAsFunction(_ food: FoodConsumedDomain, mealTypeId: String) async throws
}

struct AssignFoodMealTypeUseCase: AssignFoodMealTypeUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ food: FoodConsumedDomain, mealTypeId: String) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto = FoodConsumedDTO(food: food, mealTypeId: mealTypeId)
        try await dataProvider.setAsync(dto, id: food.id, in: Constants.Firestore.foodConsumed(userId: userId))
    }
}

#if DEBUG
final class AssignFoodMealTypeUseCaseFake: AssignFoodMealTypeUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow: Bool
    var beforeResult: (() async -> Void)?
    private(set) var calls: [(food: FoodConsumedDomain, mealTypeId: String)] = []

    // MARK: - Init

    init(shouldThrow: Bool = false) {
        self.shouldThrow = shouldThrow
    }

    // MARK: - Functions

    func callAsFunction(_ food: FoodConsumedDomain, mealTypeId: String) async throws {
        calls.append((food: food, mealTypeId: mealTypeId))
        if let hook = beforeResult {
            beforeResult = nil
            await hook()
        }
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
