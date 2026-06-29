//
//  FetchFoodsConsumedUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import CoreData

protocol FetchFoodsConsumedUseCaseProtocol {
    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain]
}

struct FetchFoodsConsumedUseCase: FetchFoodsConsumedUseCaseProtocol {

    // MARK: - Properties

    private let context: NSManagedObjectContext

    // MARK: - Init

    init(context: NSManagedObjectContext) {
        self.context = context
    }

    // MARK: - Functions

    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain] {
        try await context.perform {
            let request = NSFetchRequest<FoodConsumed>(entityName: "FoodConsumed")
            return try self.context.fetch(request).map {
                FoodConsumedDomain(
                    id: $0.id ?? "",
                    name: $0.name ?? "",
                    weight: $0.weight,
                    date: $0.date.toDate,
                    calories: Int($0.calories),
                    mealTypeId: $0.mealType.map { Int($0.id) }
                )
            }
        }
    }
}

struct FetchFoodsConsumedUseCaseFake: FetchFoodsConsumedUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(for date: Date) async throws -> [FoodConsumedDomain] { [] }
}
