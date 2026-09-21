//
//  FetchFoodsConsumedForMonthUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.07.2026.
//

import Foundation

protocol FetchFoodsConsumedForMonthUseCaseProtocol {
    func callAsFunction(for month: Date) async throws -> [FoodConsumedDomain]
}

struct FetchFoodsConsumedForMonthUseCase: FetchFoodsConsumedForMonthUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(for month: Date) async throws -> [FoodConsumedDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month], from: month)
        let startOfMonth = calendar.date(from: components) ?? month
        let startOfNextMonth = calendar.date(byAdding: .month, value: 1, to: startOfMonth) ?? startOfMonth.addingTimeInterval(Constants.Time.secondsPerDay * 31)
        let dtos: [FoodConsumedDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodConsumed(userId: userId),
            where: "date",
            isGreaterThanOrEqualTo: startOfMonth.timeIntervalSince1970,
            isLessThan: startOfNextMonth.timeIntervalSince1970
        )
        return dtos.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchFoodsConsumedForMonthUseCaseFake: FetchFoodsConsumedForMonthUseCaseProtocol {

    // MARK: - Properties

    var stubbedFoods: [FoodConsumedDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(for month: Date) async throws -> [FoodConsumedDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedFoods
    }
}
#endif
