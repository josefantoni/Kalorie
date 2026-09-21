//
//  CreateFoodItemUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import FirebaseFirestore

enum CreateFoodItemError: Error {
    case invalidCode
    case invalidName
    case invalidCalories
    case invalidWeight
    case invalidPortion(FoodPortionError)
    case itemAlreadyExists

    init(_ validationError: FoodItemValidationError) {
        self = validationError.mapped(
            invalidCode: .invalidCode,
            invalidName: .invalidName,
            invalidCalories: .invalidCalories,
            invalidWeight: .invalidWeight
        ) { .invalidPortion($0) }
    }
}

protocol CreateFoodItemUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain
}

struct CreateFoodItemUseCase: CreateFoodItemUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        if let validationError = FoodItemValidation.validate(item) {
            throw CreateFoodItemError(validationError)
        }
        let existing: FoodItemDTO? = try await dataProvider.loadFromServerAsync(
            id: item.id,
            from: Constants.Firestore.foodItems
        )
        guard existing == nil else { throw CreateFoodItemError.itemAlreadyExists }
        let dto = FoodItemDTO(item: item)
        do {
            try await dataProvider.setAsync(dto, id: item.id, in: Constants.Firestore.foodItems)
        } catch let writeError where writeError.matches(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue) {
            // Firestore's rule denial doesn't say why; a duplicate is the expected cause, but an
            // expired auth session mid-request would also deny with the same code. Re-read to
            // confirm before relabelling the failure as itemAlreadyExists.
            let confirmedExisting: FoodItemDTO? = try? await dataProvider.loadFromServerAsync(
                id: item.id,
                from: Constants.Firestore.foodItems
            )
            guard confirmedExisting != nil else { throw writeError }
            throw CreateFoodItemError.itemAlreadyExists
        }
        return item
    }
}

#if DEBUG
struct CreateFoodItemUseCaseFake: CreateFoodItemUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemDomain {
        item
    }
}
#endif
