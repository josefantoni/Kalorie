//
//  FetchFavouriteFoodsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import Foundation

protocol FetchFavouriteFoodsUseCaseProtocol {
    func callAsFunction() async throws -> [FoodItemDomain]
}

struct FetchFavouriteFoodsUseCase: FetchFavouriteFoodsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos: [FavouriteFoodDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.favouriteFoods(userId: userId),
            orderBy: "favourited_at",
            descending: true,
            limit: 50
        )
        return dtos.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchFavouriteFoodsUseCaseFake: FetchFavouriteFoodsUseCaseProtocol {

    // MARK: - Properties

    var stubbedItems: [FoodItemDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedItems
    }
}
#endif
