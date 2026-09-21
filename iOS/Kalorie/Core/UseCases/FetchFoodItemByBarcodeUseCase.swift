//
//  FetchFoodItemByBarcodeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

protocol FetchFoodItemByBarcodeUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> FoodItemDomain?
    func callAsFunction(barcodes: [String]) async throws -> [FoodItemDomain]
}

// A naive per-barcode fallback, so every existing conformer (test fakes included) keeps compiling
// without implementing this: only the real FetchFoodItemByBarcodeUseCase below overrides it with a
// genuinely batched Firestore read.
extension FetchFoodItemByBarcodeUseCaseProtocol {
    func callAsFunction(barcodes: [String]) async throws -> [FoodItemDomain] {
        try await withThrowingTaskGroup(of: FoodItemDomain?.self) { group in
            for barcode in barcodes {
                group.addTask { try await self(barcode: barcode) }
            }
            var results: [FoodItemDomain] = []
            for try await item in group {
                if let item { results.append(item) }
            }
            return results
        }
    }
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

    func callAsFunction(barcodes: [String]) async throws -> [FoodItemDomain] {
        let nonEmptyBarcodes = Array(Set(barcodes.filter { !$0.isEmpty }))
        guard !nonEmptyBarcodes.isEmpty else { return [] }
        let dtos: [FoodItemDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItems,
            whereDocumentIdIn: nonEmptyBarcodes
        )
        return dtos.map { $0.asDomain() }
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
