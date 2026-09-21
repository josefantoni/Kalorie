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
        let calendar = Calendar.current
        let dayStart = calendar.startOfDay(for: .now)
        return dtos
            .compactMap { dto -> MealTypeDomain? in
                guard
                    let start = calendar.date(byAdding: .minute, value: dto.startMinutes, to: dayStart),
                    let end = calendar.date(byAdding: .minute, value: dto.endMinutes, to: dayStart)
                else { return nil }
                return MealTypeDomain(id: dto.id, name: dto.name, startTime: start, endTime: end)
            }
            .sorted { $0.startTime < $1.startTime }
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
