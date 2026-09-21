//
//  SaveFoodItemPersonalPortionsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 09.09.2026.
//

import Foundation

protocol SaveFoodItemPersonalPortionsUseCaseProtocol {
    func callAsFunction(barcode: String, portions: [FoodPortionDomain]) async throws
}

struct SaveFoodItemPersonalPortionsUseCase: SaveFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(barcode: String, portions: [FoodPortionDomain]) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        for portion in portions {
            if let error = FoodPortionValidation.validate(name: portion.name, grams: portion.grams) { throw error }
        }
        if let error = FoodPortionValidation.validate(portions: portions) { throw error }
        let dto = FoodItemPersonalPortionsDTO(id: barcode, portions: portions.map(FoodPortionDTO.init(portion:)))
        try await dataProvider.setAsync(dto, id: barcode, in: Constants.Firestore.foodItemPortions(userId: userId))
    }
}

#if DEBUG
struct SaveFoodItemPersonalPortionsUseCaseFake: SaveFoodItemPersonalPortionsUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(barcode: String, portions: [FoodPortionDomain]) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
