//
//  FetchFoodItemReportsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

protocol FetchFoodItemReportsUseCaseProtocol {
    func callAsFunction() async throws -> [FoodItemReportDomain]
}

struct FetchFoodItemReportsUseCase: FetchFoodItemReportsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemReportDomain] {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        let dtos: [FoodItemReportDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodItemReports,
            orderBy: "reported_at",
            descending: true,
            limit: Constants.Firestore.reportsPageLimit
        )
        return dtos.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchFoodItemReportsUseCaseFake: FetchFoodItemReportsUseCaseProtocol {

    // MARK: - Properties

    var stubbedReports: [FoodItemReportDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemReportDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedReports
    }
}
#endif
