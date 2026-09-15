//
//  FetchMySubmissionsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol FetchMySubmissionsUseCaseProtocol {
    func callAsFunction() async throws -> [FoodItemSubmissionDomain]
}

struct FetchMySubmissionsUseCase: FetchMySubmissionsUseCaseProtocol {

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
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        return try await FoodItemSubmissionFetcher.fetch(where: "submitted_by", isEqualTo: userId, dataProvider: dataProvider)
    }
}

#if DEBUG
struct FetchMySubmissionsUseCaseFake: FetchMySubmissionsUseCaseProtocol {

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
