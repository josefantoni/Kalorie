//
//  UpdateMealTypeTimesUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

protocol UpdateMealTypeTimesUseCaseProtocol {
    func callAsFunction(_ mealType: MealTypeDomain) async throws
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

    func callAsFunction(_ mealType: MealTypeDomain) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto = MealTypeDTO(
            id: mealType.id,
            name: mealType.name,
            startTime: mealType.startTime.timeIntervalSince1970,
            endTime: mealType.endTime.timeIntervalSince1970
        )
        try await dataProvider.setAsync(dto, id: "\(mealType.id)", in: Constants.Firestore.mealTypes(userId: userId))
    }
}

struct UpdateMealTypeTimesUseCaseFake: UpdateMealTypeTimesUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ mealType: MealTypeDomain) async throws {}
}
