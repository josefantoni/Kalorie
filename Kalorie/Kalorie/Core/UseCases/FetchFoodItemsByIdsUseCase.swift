//
//  FetchFoodItemsByIdsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

protocol FetchFoodItemsByIdsUseCaseProtocol {
    func callAsFunction(ids: [String]) async throws -> [FoodItemDomain]
}

struct FetchFoodItemsByIdsUseCase: FetchFoodItemsByIdsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction(ids: [String]) async throws -> [FoodItemDomain] {
        let dtos: [FoodItemDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItems,
            whereDocumentIdIn: Array(Set(ids))
        )
        return dtos.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchFoodItemsByIdsUseCaseFake: FetchFoodItemsByIdsUseCaseProtocol {

    // MARK: - Properties

    var stubbedItems: [FoodItemDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(ids: [String]) async throws -> [FoodItemDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedItems
    }
}
#endif
