//
//  SaveFoodConsumedUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 01.07.2026.
//

import Foundation

protocol SaveFoodConsumedUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain, grams: Double, date: Date) async throws
}

struct SaveFoodConsumedUseCase: SaveFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, grams: Double, date: Date) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let scaled = item.scaled(toGrams: grams)
        let dto = FoodConsumedDTO(
            id: UUID().uuidString,
            foodItemId: item.id,
            foodItemKind: item.kind,
            czName: item.czName,
            engName: item.engName,
            weight: grams,
            date: date.timeIntervalSince1970,
            calories: scaled.calories,
            protein: scaled.protein,
            carbohydrate: scaled.carbohydrate,
            carbohydrateSugar: scaled.carbohydrateSugar,
            fat: scaled.fat,
            fatUnsaturated: scaled.fatUnsaturated,
            fiber: scaled.fiber,
            salt: scaled.salt
        )
        try await dataProvider.setAsync(dto, id: dto.id, in: Constants.Firestore.foodConsumed(userId: userId))
    }
}

#if DEBUG
struct SaveFoodConsumedUseCaseFake: SaveFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, grams: Double, date: Date) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
