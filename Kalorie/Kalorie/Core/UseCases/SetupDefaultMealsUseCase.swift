//
//  SetupDefaultMealsUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import CoreData

protocol SetupDefaultMealsUseCaseProtocol {
    func callAsFunction() async throws -> [MealTypeDomain]
}

struct SetupDefaultMealsUseCase: SetupDefaultMealsUseCaseProtocol {

    // MARK: - Properties

    private let context: NSManagedObjectContext

    // MARK: - Init

    init(context: NSManagedObjectContext) {
        self.context = context
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] {
        try await context.perform {
            guard var startTime = Calendar.current.date(bySettingHour: 5, minute: 0, second: 0, of: Date.now) else {
                fatalError("Failed to create default start time in SetupDefaultMealsUseCase")
            }
            var endTime = startTime.withAddedHours(hours: 3)
            var id = 0
            let mealNames = ["Snídaně", "Druhá snídaně", "Oběd", "Svačina", "Večeře"]
            var domains: [MealTypeDomain] = []

            for mealName in mealNames {
                _ = MealType(id: id, name: mealName, startTime: startTime, endTime: endTime, context: self.context)
                domains.append(MealTypeDomain(id: id, name: mealName, startTime: startTime, endTime: endTime))
                startTime = endTime
                endTime = startTime.withAddedHours(hours: 3)
                id += 1
            }
            try self.context.save()
            return domains
        }
    }
}

struct SetupDefaultMealsUseCaseFake: SetupDefaultMealsUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] { [] }
}
