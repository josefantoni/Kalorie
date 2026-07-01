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
        let dtos: [FoodConsumedDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.foodConsumed(userId: userId))
        return dtos
            .filter { Calendar.current.isDate(Date(timeIntervalSince1970: $0.date), inSameDayAs: date) }
            .map {
                FoodConsumedDomain(
                    id: $0.id,
                    name: $0.name,
                    weight: $0.weight,
                    date: Date(timeIntervalSince1970: $0.date),
                    calories: $0.calories
                )
            }
    }
}

struct FetchFoodsConsumedUseCaseFake: FetchFoodsConsumedUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain] { [] }
}
