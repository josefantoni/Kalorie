//
//  CreateMealTypeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import MealKit

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
        let startMinutes = startTime.minutesSinceMidnight
        let endMinutes = endTime.minutesSinceMidnight
        guard MealWindowsKt.isMealWindowLongEnough(startMinutes: startMinutes, endMinutes: endMinutes, minimumDurationMinutes: 30) else {
            throw CreateMealTypeError.durationTooShort
        }
        guard !existingMealTypes.contains(where: {
            MealWindowsKt.mealWindowsOverlap(
                startMinutes: startMinutes,
                endMinutes: endMinutes,
                otherStartMinutes: $0.startTime.minutesSinceMidnight,
                otherEndMinutes: $0.endTime.minutesSinceMidnight
            )
        }) else {
            throw CreateMealTypeError.timeConflict
        }
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let newId = UUID().uuidString
        let dto = MealTypeDTO(
            id: newId,
            name: name,
            startMinutes: Int(startMinutes),
            endMinutes: Int(endMinutes)
        )
        try await dataProvider.setAsync(dto, id: newId, in: Constants.Firestore.mealTypes(userId: userId))
        return MealTypeDomain(id: newId, name: name, startTime: startTime, endTime: endTime)
    }
}

#if DEBUG
struct CreateMealTypeUseCaseFake: CreateMealTypeUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(
        name: String,
        startTime: Date,
        endTime: Date,
        existingMealTypes: [MealTypeDomain]
    ) async throws -> MealTypeDomain {
        MealTypeDomain(id: UUID().uuidString, name: name, startTime: startTime, endTime: endTime)
    }
}
#endif
