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
        let ratio = grams / 100
        let dto = FoodConsumedDTO(
            id: UUID().uuidString,
            czName: item.czName,
            engName: item.engName,
            weight: grams,
            date: date.timeIntervalSince1970,
            calories: Int(item.caloriesPerHundredGrams * ratio),
            protein: item.protein * ratio,
            carbohydrate: item.carbohydrate * ratio,
            carbohydrateSugar: item.carbohydratePureSugar * ratio,
            fat: item.fat * ratio,
            fatUnsaturated: item.fatUnsaturatedFattyAcids * ratio,
            fiber: item.fiber * ratio,
            salt: item.salt * ratio
        )
        try await dataProvider.saveAsync(dto, to: Constants.Firestore.foodConsumed(userId: userId))
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
