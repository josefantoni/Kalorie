//
//  SetupDefaultMealsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

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
        let boundaries = [5 * 60, 8 * 60 + 30, 11 * 60, 14 * 60 + 30, 17 * 60, 20 * 60]
        let mealNames = [
            L10n.DefaultMeals.breakfast,
            L10n.DefaultMeals.secondBreakfast,
            L10n.DefaultMeals.lunch,
            L10n.DefaultMeals.snack,
            L10n.DefaultMeals.dinner
        ]
        var dtos: [(item: MealTypeDTO, id: String)] = []
        var domains: [MealTypeDomain] = []

        for (index, mealName) in mealNames.enumerated() {
            let startMinutes = boundaries[index]
            let endMinutes = boundaries[index + 1]
            let id = UUID().uuidString
            dtos.append((
                item: MealTypeDTO(
                    id: id,
                    name: mealName,
                    startMinutes: startMinutes,
                    endMinutes: endMinutes
                ),
                id: id
            ))
            domains.append(MealTypeDomain(id: id, name: mealName, startMinutes: startMinutes, endMinutes: endMinutes))
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
