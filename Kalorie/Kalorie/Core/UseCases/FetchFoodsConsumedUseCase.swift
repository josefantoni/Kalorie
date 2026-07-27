//
//  FetchFoodsConsumedUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

protocol FetchFoodsConsumedUseCaseProtocol {
    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain]
}

struct FetchFoodsConsumedUseCase: FetchFoodsConsumedUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let startOfDay = Calendar.current.startOfDay(for: date)
        let startOfNextDay = Calendar.current.date(byAdding: .day, value: 1, to: startOfDay) ?? startOfDay.addingTimeInterval(Constants.Time.secondsPerDay)
        let dtos: [FoodConsumedDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.foodConsumed(userId: userId),
            where: "date",
            isGreaterThanOrEqualTo: startOfDay.timeIntervalSince1970,
            isLessThan: startOfNextDay.timeIntervalSince1970
        )
        return dtos.map {
            FoodConsumedDomain(
                id: $0.id,
                czName: $0.czName,
                engName: $0.engName,
                weight: $0.weight,
                date: Date(timeIntervalSince1970: $0.date),
                calories: $0.calories
            )
        }
    }
}

#if DEBUG
struct FetchFoodsConsumedUseCaseFake: FetchFoodsConsumedUseCaseProtocol {

    // MARK: - Properties

    var stubbedFoods: [FoodConsumedDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedFoods
    }
}
#endif
