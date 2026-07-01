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
        guard var startTime = Calendar.current.date(bySettingHour: 5, minute: 0, second: 0, of: Date.now) else {
            fatalError("Failed to create default start time in SetupDefaultMealsUseCase")
        }
        var endTime = startTime.withAddedHours(hours: 3)
        let mealNames = [
            L10n.DefaultMeals.breakfast,
            L10n.DefaultMeals.secondBreakfast,
            L10n.DefaultMeals.lunch,
            L10n.DefaultMeals.snack,
            L10n.DefaultMeals.dinner
        ]
        var dtos: [(item: MealTypeDTO, id: String)] = []
        var domains: [MealTypeDomain] = []

        for (id, mealName) in mealNames.enumerated() {
            dtos.append((
                item: MealTypeDTO(id: id, name: mealName, startTime: startTime.timeIntervalSince1970, endTime: endTime.timeIntervalSince1970),
                id: "\(id)"
            ))
            domains.append(MealTypeDomain(id: id, name: mealName, startTime: startTime, endTime: endTime))
            startTime = endTime
            endTime = startTime.withAddedHours(hours: 3)
        }

        try await dataProvider.batchSetAsync(dtos, in: Constants.Firestore.mealTypes(userId: userId))
        return domains
    }
}

struct SetupDefaultMealsUseCaseFake: SetupDefaultMealsUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] { [] }
}
