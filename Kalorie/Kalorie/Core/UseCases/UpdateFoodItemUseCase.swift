//
//  UpdateFoodItemUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

enum UpdateFoodItemError: Error {
    case invalidCode
    case invalidName
    case invalidCalories
    case invalidWeight
    case invalidPortion(FoodPortionError)

    init(_ validationError: FoodItemValidationError) {
        self = validationError.mapped(
            invalidCode: .invalidCode,
            invalidName: .invalidName,
            invalidCalories: .invalidCalories,
            invalidWeight: .invalidWeight
        ) { .invalidPortion($0) }
    }
}

protocol UpdateFoodItemUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws
}

struct UpdateFoodItemUseCase: UpdateFoodItemUseCaseProtocol {

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
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        if let validationError = FoodItemValidation.validate(item) {
            throw UpdateFoodItemError(validationError)
        }
        let dto = FoodItemDTO(item: item)
        try await dataProvider.setAsync(dto, id: item.id, in: Constants.Firestore.foodItems)
    }
}

#if DEBUG
struct UpdateFoodItemUseCaseFake: UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
