//
//  ApproveSubmissionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol ApproveSubmissionUseCaseProtocol {
    func callAsFunction(id: String, item: FoodItemDomain) async throws
}

struct ApproveSubmissionUseCase: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.createFoodItem = createFoodItem
    }

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        _ = try await createFoodItem(item)
        try await dataProvider.deleteAsync(id: id, from: Constants.Firestore.foodItemSubmissions)
    }
}

#if DEBUG
struct ApproveSubmissionUseCaseFake: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
