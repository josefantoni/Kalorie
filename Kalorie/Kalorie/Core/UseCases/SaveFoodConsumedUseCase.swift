//
//  SaveFoodConsumedUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 01.07.2026.
//

import Foundation

protocol SaveFoodConsumedUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain, date: Date) async throws
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

    func callAsFunction(_ item: FoodItemDomain, date: Date) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let calories = Int(item.caloriesPerHundredGrams * item.weight / 100)
        let dto = FoodConsumedDTO(
            id: item.id,
            czName: item.czName,
            engName: item.engName,
            weight: item.weight,
            date: date.timeIntervalSince1970,
            calories: calories
        )
        try await dataProvider.saveAsync(dto, to: Constants.Firestore.foodConsumed(userId: userId))
    }
}

struct SaveFoodConsumedUseCaseFake: SaveFoodConsumedUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, date: Date) async throws {}
}
