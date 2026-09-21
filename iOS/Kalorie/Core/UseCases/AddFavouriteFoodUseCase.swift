//
//  AddFavouriteFoodUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import Foundation

protocol AddFavouriteFoodUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws
}

struct AddFavouriteFoodUseCase: AddFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto = FavouriteFoodDTO(item: item, favouritedAt: .now)
        try await dataProvider.setAsync(dto, id: item.id, in: Constants.Firestore.favouriteFoods(userId: userId))
    }
}

#if DEBUG
struct AddFavouriteFoodUseCaseFake: AddFavouriteFoodUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
