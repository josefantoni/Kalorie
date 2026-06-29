//
//  CreateFoodItemUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

enum CreateFoodItemError: Error {
    case invalidCode
    case invalidName
    case invalidCalories
    case invalidWeight
}

protocol CreateFoodItemUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain
}

struct CreateFoodItemUseCase: CreateFoodItemUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        guard !item.id.isEmpty && item.id.allSatisfy({ $0.isNumber }) else { throw CreateFoodItemError.invalidCode }
        guard !item.name.isEmpty else { throw CreateFoodItemError.invalidName }
        guard item.caloriesPerHundredGrams > 0 else { throw CreateFoodItemError.invalidCalories }
        guard item.weight > 0 else { throw CreateFoodItemError.invalidWeight }
        let dto = FoodItemDTO(
            id: item.id,
            name: item.name,
            weight: item.weight,
            date: item.date.timeIntervalSince1970,
            caloriesPerHundredGrams: item.caloriesPerHundredGrams,
            fat: item.fat,
            fatUnsaturatedFattyAcids: item.fatUnsaturatedFattyAcids,
            carbohydrate: item.carbohydrate,
            carbohydratePureSugar: item.carbohydratePureSugar,
            protein: item.protein,
            salt: item.salt
        )
        try await dataProvider.saveAsync(dto, to: "food_items")
        return item
    }
}

struct CreateFoodItemUseCaseFake: CreateFoodItemUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        item
    }
}
