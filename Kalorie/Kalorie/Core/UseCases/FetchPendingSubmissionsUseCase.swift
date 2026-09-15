//
//  FetchPendingSubmissionsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol FetchPendingSubmissionsUseCaseProtocol {
    func callAsFunction() async throws -> [FoodItemSubmissionDomain]
}

struct FetchPendingSubmissionsUseCase: FetchPendingSubmissionsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemSubmissionDomain] {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        return try await FoodItemSubmissionFetcher.fetch(
            where: "status",
            isEqualTo: FoodItemSubmissionStatus.pending.rawValue,
            dataProvider: dataProvider
        )
    }
}

#if DEBUG
struct FetchPendingSubmissionsUseCaseFake: FetchPendingSubmissionsUseCaseProtocol {

    // MARK: - Properties

    var stubbedSubmissions: [FoodItemSubmissionDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemSubmissionDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedSubmissions
    }
}
#endif
