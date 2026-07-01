//
//  CreateMealTypeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

protocol CreateMealTypeUseCaseProtocol {
    func callAsFunction(
        name: String,
        startTime: Date,
        endTime: Date,
        existingMealTypes: [MealTypeDomain]
    ) async throws -> MealTypeDomain
}

struct CreateMealTypeUseCase: CreateMealTypeUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(
        name: String,
        startTime: Date,
        endTime: Date,
        existingMealTypes: [MealTypeDomain]
    ) async throws -> MealTypeDomain {
        guard !name.isEmpty else { throw CreateMealTypeError.emptyName }
        guard !existingMealTypes.contains(where: { $0.name == name }) else {
            throw CreateMealTypeError.duplicateName
        }
        guard endTime.timeIntervalSince(startTime) >= 30 * 60 else {
            throw CreateMealTypeError.durationTooShort
        }
        guard !existingMealTypes.contains(where: {
            startTime < $0.endTime && endTime > $0.startTime
        }) else {
            throw CreateMealTypeError.timeConflict
        }
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let newId = (existingMealTypes.map { $0.id }.max() ?? -1) + 1
        let dto = MealTypeDTO(
            id: newId,
            name: name,
            startTime: startTime.timeIntervalSince1970,
            endTime: endTime.timeIntervalSince1970
        )
        try await dataProvider.setAsync(dto, id: "\(newId)", in: Constants.Firestore.mealTypes(userId: userId))
        return MealTypeDomain(id: newId, name: name, startTime: startTime, endTime: endTime)
    }
}

struct CreateMealTypeUseCaseFake: CreateMealTypeUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(
        name: String,
        startTime: Date,
        endTime: Date,
        existingMealTypes: [MealTypeDomain]
    ) async throws -> MealTypeDomain {
        MealTypeDomain(id: 0, name: name, startTime: startTime, endTime: endTime)
    }
}
