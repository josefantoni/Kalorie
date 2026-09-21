//
//  FetchFoodsConsumedInRangeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

protocol FetchFoodsConsumedInRangeUseCaseProtocol {
    func callAsFunction(from: Date, to: Date) async throws -> [FoodConsumedDomain]
}

struct FetchFoodsConsumedInRangeUseCase: FetchFoodsConsumedInRangeUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(from: Date, to: Date) async throws -> [FoodConsumedDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: from)
        let endDay = calendar.startOfDay(for: to)
        let end = calendar.date(byAdding: .day, value: 1, to: endDay) ?? endDay.addingTimeInterval(Constants.Time.secondsPerDay)
        let dtos: [FoodConsumedDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodConsumed(userId: userId),
            where: "date",
            isGreaterThanOrEqualTo: start.timeIntervalSince1970,
            isLessThan: end.timeIntervalSince1970
        )
        return dtos.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchFoodsConsumedInRangeUseCaseFake: FetchFoodsConsumedInRangeUseCaseProtocol {

    // MARK: - Properties

    var stubbedFoods: [FoodConsumedDomain] = []
    var stubbedError: Error?

    // MARK: - Functions

    func callAsFunction(from: Date, to: Date) async throws -> [FoodConsumedDomain] {
        if let stubbedError { throw stubbedError }
        return stubbedFoods
    }
}
#endif
