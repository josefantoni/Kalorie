//
//  FetchMealTypesUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import CoreData

protocol FetchMealTypesUseCaseProtocol {
    func callAsFunction() async throws -> [MealTypeDomain]
}

struct FetchMealTypesUseCase: FetchMealTypesUseCaseProtocol {

    // MARK: - Properties

    private let context: NSManagedObjectContext

    // MARK: - Init

    init(context: NSManagedObjectContext) {
        self.context = context
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] {
        try await context.perform {
            let request = NSFetchRequest<MealType>(entityName: Constants.CoreData.EntityName.mealType)
            return try self.context.fetch(request).map {
                MealTypeDomain(
                    id: Int($0.id),
                    name: $0.name ?? "",
                    startTime: $0.startTime.toDate,
                    endTime: $0.endTime.toDate
                )
            }
        }
    }
}

struct FetchMealTypesUseCaseFake: FetchMealTypesUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction() async throws -> [MealTypeDomain] { [] }
}
