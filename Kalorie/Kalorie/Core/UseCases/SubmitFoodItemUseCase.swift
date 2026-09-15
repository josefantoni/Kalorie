//
//  SubmitFoodItemUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol SubmitFoodItemUseCaseProtocol {
    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemSubmissionDomain
}

struct SubmitFoodItemUseCase: SubmitFoodItemUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemSubmissionDomain {
        try await FoodItemSubmissionWriter.write(
            id: UUID().uuidString,
            item: item,
            dataProvider: dataProvider,
            authProvider: authProvider
        )
    }
}

#if DEBUG
struct SubmitFoodItemUseCaseFake: SubmitFoodItemUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain) async throws -> FoodItemSubmissionDomain {
        if let errorToThrow { throw errorToThrow }
        return FoodItemSubmissionDomain(
            id: UUID().uuidString,
            barcode: item.id,
            submittedBy: "test-user-id",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: item
        )
    }
}
#endif
