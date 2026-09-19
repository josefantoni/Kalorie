//
//  RefreshFavouriteFoodUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

protocol RefreshFavouriteFoodUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain
}

struct RefreshFavouriteFoodUseCase: RefreshFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        guard item.kind == .catalogue else { return item }
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let catalogueDTO: FoodItemDTO? = try await dataProvider.loadAsync(id: item.id, from: Constants.Firestore.foodItems)
        guard let fresh = catalogueDTO?.asDomain(), fresh != item else { return item }
        let collection = Constants.Firestore.favouriteFoods(userId: userId)
        let existing: FavouriteFoodDTO? = try await dataProvider.loadAsync(id: item.id, from: collection)
        guard let existing else { return fresh }
        let dto = FavouriteFoodDTO(item: fresh, favouritedAt: Date(timeIntervalSince1970: existing.favouritedAt))
        try await dataProvider.setAsync(dto, id: item.id, in: collection)
        return fresh
    }
}

#if DEBUG
struct RefreshFavouriteFoodUseCaseFake: RefreshFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    var stubbedItem: FoodItemDomain?
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedItem ?? item
    }
}
#endif
