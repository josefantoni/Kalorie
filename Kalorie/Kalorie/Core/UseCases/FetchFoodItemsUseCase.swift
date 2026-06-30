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
        let dtos: [FoodItemDTO] = try await dataProvider.loadAsync("food_items")
        return dtos.map {
            FoodItemDomain(
                id: $0.id,
                name: $0.name,
                weight: $0.weight,
                date: $0.date.toDate,
                caloriesPerHundredGrams: $0.caloriesPerHundredGrams,
                fat: $0.fat,
                fatUnsaturatedFattyAcids: $0.fatUnsaturatedFattyAcids,
                carbohydrate: $0.carbohydrate,
                carbohydratePureSugar: $0.carbohydratePureSugar,
                protein: $0.protein,
                salt: $0.salt
            )
        }
    }
}

struct FetchFoodItemsUseCaseFake: FetchFoodItemsUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemDomain] { [] }
}
