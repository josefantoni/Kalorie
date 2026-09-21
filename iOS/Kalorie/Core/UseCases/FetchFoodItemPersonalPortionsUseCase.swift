//
//  FetchFoodItemPersonalPortionsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import Foundation

protocol FetchFoodItemPersonalPortionsUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> [FoodPortionDomain]
}

struct FetchFoodItemPersonalPortionsUseCase: FetchFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> [FoodPortionDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto: FoodItemPersonalPortionsDTO? = try await dataProvider.loadAsync(
            id: barcode,
            from: Constants.Firestore.foodItemPortions(userId: userId)
        )
        return dto?.portions.map { $0.asDomain() } ?? []
    }
}

#if DEBUG
struct FetchFoodItemPersonalPortionsUseCaseFake: FetchFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Properties

    var stubbedPortions: [FoodPortionDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> [FoodPortionDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedPortions
    }
}
#endif
