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
        let today = Date.now
        return dtos
            .compactMap { dto -> MealTypeDomain? in
                guard
                    let start = calendar.date(bySettingHour: dto.startMinutes / 60, minute: dto.startMinutes % 60, second: 0, of: today),
                    let end = calendar.date(bySettingHour: dto.endMinutes / 60, minute: dto.endMinutes % 60, second: 0, of: today)
                else { return nil }
                return MealTypeDomain(id: dto.id, name: dto.name, startTime: start, endTime: end)
            }
            .sorted { $0.startTime < $1.startTime }
    }
}

#if DEBUG
struct FetchMealTypesUseCaseFake: FetchMealTypesUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] { [] }
}
#endif
