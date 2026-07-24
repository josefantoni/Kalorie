//
//  FetchFoodItemByBarcodeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

protocol FetchFoodItemByBarcodeUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> FoodItemDomain?
}

struct FetchFoodItemByBarcodeUseCase: FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? {
        guard !barcode.isEmpty else { return nil }
        let dto: FoodItemDTO? = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItems,
            where: "id",
            isEqualTo: barcode
        )
        guard let dto else { return nil }
        return FoodItemDomain(
            id: dto.id,
            czName: dto.czName,
            engName: dto.engName,
            weight: dto.weight,
            date: dto.date.toDate,
            energyKJ: dto.energyKJ ?? 0,
            caloriesPerHundredGrams: dto.caloriesPerHundredGrams,
            fat: dto.fat,
            fatSaturated: dto.fatSaturated ?? 0,
            fatUnsaturatedFattyAcids: dto.fatUnsaturatedFattyAcids,
            carbohydrate: dto.carbohydrate,
            carbohydratePureSugar: dto.carbohydratePureSugar,
            fiber: dto.fiber ?? 0,
            protein: dto.protein,
            salt: dto.salt
        )
    }
}

struct FetchFoodItemByBarcodeUseCaseFake: FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Properties

    var stubbedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? { stubbedItem }
}
