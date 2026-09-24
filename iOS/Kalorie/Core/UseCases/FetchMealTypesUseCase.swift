//
//  FetchMealTypesUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

protocol FetchMealTypesUseCaseProtocol {
    func callAsFunction() async throws -> [MealTypeDomain]
}

struct FetchMealTypesUseCase: FetchMealTypesUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos: [MealTypeDTO] = try await dataProvider.loadAsync(from: Constants.Firestore.mealTypes(userId: userId))
        return dtos
            .map { MealTypeDomain(id: $0.id, name: $0.name, startMinutes: $0.startMinutes, endMinutes: $0.endMinutes) }
            .sorted { $0.startMinutes < $1.startMinutes }
    }
}

#if DEBUG
struct FetchMealTypesUseCaseFake: FetchMealTypesUseCaseProtocol {

    // MARK: - Properties

    var stubbedTypes: [MealTypeDomain] = []
    var shouldThrow = false
    var errorToThrow: Error = URLError(.unknown)

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] {
        if shouldThrow { throw errorToThrow }
        return stubbedTypes
    }
}
#endif
