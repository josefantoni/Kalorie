//
//  SearchFoodItemsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.07.2026.
//

import Foundation

protocol SearchFoodItemsUseCaseProtocol {
    func callAsFunction(query: String) async throws -> [FoodItemDomain]
}

struct SearchFoodItemsUseCase: SearchFoodItemsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction(query: String) async throws -> [FoodItemDomain] {
        let lowercasedQuery = query.lowercased()
        async let byName: [FoodItemDTO] = dataProvider.loadAsync(
            from: Constants.Firestore.foodItems,
            where: "cz_name_lowercase",
            hasPrefix: lowercasedQuery,
            limit: 10
        )
        async let byOriginalName: [FoodItemDTO] = dataProvider.loadAsync(
            from: Constants.Firestore.foodItems,
            where: "eng_name_lowercase",
            hasPrefix: lowercasedQuery,
            limit: 10
        )
        let (nameResults, originalNameResults) = try await (byName, byOriginalName)
        var seen = Set<String>()
        return (nameResults + originalNameResults)
            .filter { seen.insert($0.id).inserted }
            .map { $0.asDomain() }
    }
}

#if DEBUG
struct SearchFoodItemsUseCaseFake: SearchFoodItemsUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false
    var stubbedItems: [FoodItemDomain] = []

    // MARK: - Functions

    func callAsFunction(query: String) async throws -> [FoodItemDomain] {
        if shouldThrow { throw NSError(domain: "SearchFoodItemsUseCaseFake", code: 0) }
        return stubbedItems
    }
}
#endif
