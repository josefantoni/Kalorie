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
    case changedSinceLoad

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
    func callAsFunction(_ item: FoodItemDomain, previouslyLoaded: FoodItemDomain) async throws
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

    // setAsync replaces the whole document, so a maintainer editing a stale copy (e.g. two
    // catalogue-editor sessions on the same barcode) would otherwise silently discard whatever
    // changed in between — the same class of race ADR 0029 closed for foodItemSubmissions, but
    // foodItems has no submitted_at-like field to reuse as a token, so this re-reads and compares
    // the full document instead, client-side only (no firestore.rules enforcement, same trust
    // boundary ADR 0029 already accepted for ApproveSubmissionUseCase's own re-read).
    func callAsFunction(_ item: FoodItemDomain, previouslyLoaded: FoodItemDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        if let validationError = FoodItemValidation.validate(item) {
            throw UpdateFoodItemError(validationError)
        }
        let current: FoodItemDTO? = try await dataProvider.loadAsync(id: item.id, from: Constants.Firestore.foodItems)
        guard current?.asDomain() == previouslyLoaded else {
            throw UpdateFoodItemError.changedSinceLoad
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

    func callAsFunction(_ item: FoodItemDomain, previouslyLoaded: FoodItemDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
