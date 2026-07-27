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
    case itemAlreadyExists
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
        guard !item.czName.isEmpty else { throw CreateFoodItemError.invalidName }
        guard item.caloriesPerHundredGrams > 0 else { throw CreateFoodItemError.invalidCalories }
        guard item.weight > 0 else { throw CreateFoodItemError.invalidWeight }
        let existing: FoodItemDTO? = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItems,
            where: "id",
            isEqualTo: item.id
        )
        guard existing == nil else { throw CreateFoodItemError.itemAlreadyExists }
        let dto = FoodItemDTO(
            id: item.id,
            czName: item.czName,
            engName: item.engName,
            czNameLowercase: item.czName.lowercased(),
            engNameLowercase: item.engName.lowercased(),
            weight: item.weight,
            date: item.date.timeIntervalSince1970,
            energyKJ: item.energyKJ,
            caloriesPerHundredGrams: item.caloriesPerHundredGrams,
            fat: item.fat,
            fatSaturated: item.fatSaturated,
            fatUnsaturatedFattyAcids: item.fatUnsaturatedFattyAcids,
            carbohydrate: item.carbohydrate,
            carbohydratePureSugar: item.carbohydratePureSugar,
            fiber: item.fiber,
            protein: item.protein,
            salt: item.salt
        )
        try await dataProvider.setAsync(dto, id: item.id, in: Constants.Firestore.foodItems)
        return item
    }
}

#if DEBUG
struct CreateFoodItemUseCaseFake: CreateFoodItemUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        item
    }
}
#endif
