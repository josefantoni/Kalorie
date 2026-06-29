//
//  CreateMealTypeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import CoreData

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

    private let context: NSManagedObjectContext

    // MARK: - Init

    init(context: NSManagedObjectContext) {
        self.context = context
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
        guard !existingMealTypes.contains(where: {
            startTime.isBetween($0.startTime, $0.endTime) ||
            endTime.isBetween($0.startTime, $0.endTime)
        }) else {
            throw CreateMealTypeError.timeConflict
        }
        let newId = (existingMealTypes.map { $0.id }.max() ?? -1) + 1
        return try await context.perform {
            _ = MealType(id: newId, name: name, startTime: startTime, endTime: endTime, context: self.context)
            try self.context.save()
            return MealTypeDomain(id: newId, name: name, startTime: startTime, endTime: endTime)
        }
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
