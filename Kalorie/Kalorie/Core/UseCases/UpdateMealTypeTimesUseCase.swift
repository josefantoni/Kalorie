//
//  UpdateMealTypeTimesUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

protocol UpdateMealTypeTimesUseCaseProtocol {
    func callAsFunction(_ mealTypes: [MealTypeDomain]) async throws
}

struct UpdateMealTypeTimesUseCase: UpdateMealTypeTimesUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ mealTypes: [MealTypeDomain]) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dtos = mealTypes.map { mealType in
            (
                item: MealTypeDTO(
                    id: mealType.id,
                    name: mealType.name,
                    startTime: mealType.startTime.timeIntervalSince1970,
                    endTime: mealType.endTime.timeIntervalSince1970
                ),
                id: "\(mealType.id)"
            )
        }
        try await dataProvider.batchSetAsync(dtos, in: Constants.Firestore.mealTypes(userId: userId))
    }
}

struct UpdateMealTypeTimesUseCaseFake: UpdateMealTypeTimesUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ mealTypes: [MealTypeDomain]) async throws {}
}
