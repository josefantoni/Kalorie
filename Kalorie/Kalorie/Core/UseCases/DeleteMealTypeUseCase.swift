//
//  DeleteMealTypeUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import CoreData

protocol DeleteMealTypeUseCaseProtocol {
    func callAsFunction(_ mealType: MealTypeDomain) async throws
}

struct DeleteMealTypeUseCase: DeleteMealTypeUseCaseProtocol {

    // MARK: - Properties

    private let context: NSManagedObjectContext

    // MARK: - Init

    init(context: NSManagedObjectContext) {
        self.context = context
    }

    // MARK: - Functions

    func callAsFunction(_ mealType: MealTypeDomain) async throws {
        try await context.perform {
            let request = NSFetchRequest<MealType>(entityName: Constants.CoreData.EntityName.mealType)
            request.predicate = NSPredicate(format: "id == %d", mealType.id)
            if let found = try self.context.fetch(request).first {
                self.context.delete(found)
            }
            try self.context.save()
        }
    }
}

struct DeleteMealTypeUseCaseFake: DeleteMealTypeUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(_ mealType: MealTypeDomain) async throws {}
}
