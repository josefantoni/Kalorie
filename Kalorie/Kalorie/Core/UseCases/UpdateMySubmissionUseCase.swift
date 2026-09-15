//
//  UpdateMySubmissionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol UpdateMySubmissionUseCaseProtocol {
    func callAsFunction(id: String, item: FoodItemDomain) async throws -> FoodItemSubmissionDomain
}

struct UpdateMySubmissionUseCase: UpdateMySubmissionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain) async throws -> FoodItemSubmissionDomain {
        try await FoodItemSubmissionWriter.write(
            id: id,
            item: item,
            dataProvider: dataProvider,
            authProvider: authProvider
        )
    }
}

#if DEBUG
struct UpdateMySubmissionUseCaseFake: UpdateMySubmissionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain) async throws -> FoodItemSubmissionDomain {
        if let errorToThrow { throw errorToThrow }
        return FoodItemSubmissionDomain(
            id: id,
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
