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
            id: barcode,
            from: Constants.Firestore.foodItems
        )
        return dto?.asDomain()
    }
}

#if DEBUG
struct FetchFoodItemByBarcodeUseCaseFake: FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Properties

    var stubbedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? { stubbedItem }
}
#endif
