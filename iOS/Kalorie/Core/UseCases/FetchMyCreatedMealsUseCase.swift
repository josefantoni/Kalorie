//
//  FetchMyCreatedMealsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation

protocol FetchMyCreatedMealsUseCaseProtocol {
    func callAsFunction() async throws -> [MyCreatedMealDomain]
}

struct FetchMyCreatedMealsUseCase: FetchMyCreatedMealsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [MyCreatedMealDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos: [MyCreatedMealDTO] = try await dataProvider.loadAsync(
            from: Constants.Firestore.myCreatedMeals(userId: userId),
            orderBy: "updated_at",
            descending: true,
            limit: 50
        )
        return dtos.map { $0.asDomain() }
    }
}

#if DEBUG
struct FetchMyCreatedMealsUseCaseFake: FetchMyCreatedMealsUseCaseProtocol {

    // MARK: - Properties

    var stubbedMeals: [MyCreatedMealDomain] = []
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> [MyCreatedMealDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedMeals
    }
}
#endif
