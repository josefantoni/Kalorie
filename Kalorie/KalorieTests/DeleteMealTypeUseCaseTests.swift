//
//  DeleteMealTypeUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
import CoreData
@testable import Kalorie

final class DeleteMealTypeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_deleteMealType_withExistingId_removesFromContext() async throws {
        let (sut, context) = makeSUT()
        _ = MealType(id: 1, name: "Snídaně", startTime: makeDate(hour: 6), endTime: makeDate(hour: 9), context: context)
        try context.save()

        try await sut(MealTypeDomain(id: 1, name: "Snídaně", startTime: makeDate(hour: 6), endTime: makeDate(hour: 9)))

        let request = NSFetchRequest<MealType>(entityName: Constants.CoreData.EntityName.mealType)
        let remaining = try context.fetch(request)
        XCTAssertTrue(remaining.isEmpty)
    }

    func test_deleteMealType_withNonExistingId_doesNotThrow() async throws {
        let (sut, _) = makeSUT()
        try await sut(MealTypeDomain(id: 99, name: "Ghost", startTime: makeDate(hour: 0), endTime: makeDate(hour: 1)))
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: DeleteMealTypeUseCase, context: NSManagedObjectContext) {
        let context = makeInMemoryContext()
        let sut = DeleteMealTypeUseCase(context: context)
        return (sut, context)
    }

    private func makeInMemoryContext() -> NSManagedObjectContext {
        let container = NSPersistentContainer(name: "Model")
        let description = NSPersistentStoreDescription()
        description.type = NSInMemoryStoreType
        container.persistentStoreDescriptions = [description]
        container.loadPersistentStores { _, _ in }
        return container.viewContext
    }

    private func makeDate(hour: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: Date()) ?? Date()
    }
}
