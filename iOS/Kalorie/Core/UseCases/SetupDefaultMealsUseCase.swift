//
//  SetupDefaultMealsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import MealKit

protocol SetupDefaultMealsUseCaseProtocol {
    func callAsFunction() async throws -> [MealTypeDomain]
}

struct SetupDefaultMealsUseCase: SetupDefaultMealsUseCaseProtocol {

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
        var dtos: [(item: MealTypeDTO, id: String)] = []
        var domains: [MealTypeDomain] = []

        for window in MealWindowsKt.DEFAULT_MEAL_WINDOWS {
            let defaultKey = window.key
            let mealName = L10n.DefaultMeals.name(forKey: defaultKey) ?? defaultKey
            let startMinutes = Int(window.startMinutes)
            let endMinutes = Int(window.endMinutes)
            let id = UUID().uuidString
            dtos.append((
                item: MealTypeDTO(
                    id: id,
                    name: mealName,
                    startMinutes: startMinutes,
                    endMinutes: endMinutes,
                    defaultKey: defaultKey
                ),
                id: id
            ))
            domains.append(MealTypeDomain(
                id: id,
                name: mealName,
                startMinutes: startMinutes,
                endMinutes: endMinutes,
                defaultKey: defaultKey
            ))
        }

        try await dataProvider.batchSetAsync(dtos, in: Constants.Firestore.mealTypes(userId: userId))
        return domains
    }
}

#if DEBUG
struct SetupDefaultMealsUseCaseFake: SetupDefaultMealsUseCaseProtocol {

    // MARK: - Properties

    var stubbedTypes: [MealTypeDomain] = []

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] { stubbedTypes }
}
#endif
