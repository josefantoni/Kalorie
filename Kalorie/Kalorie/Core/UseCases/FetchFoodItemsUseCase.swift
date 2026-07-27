//
//  FetchFoodItemsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

protocol FetchFoodItemsUseCaseProtocol {
    func callAsFunction() async throws -> [FoodItemDomain]
}

struct FetchFoodItemsUseCase: FetchFoodItemsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemDomain] {
        let dtos: [FoodItemDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.foodItems)
        return dtos.map {
            FoodItemDomain(
                id: $0.id,
                czName: $0.czName,
                engName: $0.engName,
                weight: $0.weight,
                date: $0.date.toDate,
                energyKJ: $0.energyKJ ?? 0,
                caloriesPerHundredGrams: $0.caloriesPerHundredGrams,
                fat: $0.fat,
                fatSaturated: $0.fatSaturated ?? 0,
                fatUnsaturatedFattyAcids: $0.fatUnsaturatedFattyAcids,
                carbohydrate: $0.carbohydrate,
                carbohydratePureSugar: $0.carbohydratePureSugar,
                fiber: $0.fiber ?? 0,
                protein: $0.protein,
                salt: $0.salt
            )
        }
    }
}

#if DEBUG
struct FetchFoodItemsUseCaseFake: FetchFoodItemsUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemDomain] { [] }
}
#endif
