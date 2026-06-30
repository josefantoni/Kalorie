//
//  SetupDefaultMealsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
import CoreData
@testable import Kalorie

final class SetupDefaultMealsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_setupDefaultMeals_returnsExactlyFiveMealTypes() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertEqual(result.count, 5)
    }

    func test_setupDefaultMeals_persistsFiveMealTypesToContext() async throws {
        let (sut, context) = makeSUT()
        _ = try await sut()

        let request = NSFetchRequest<MealType>(entityName: Constants.CoreData.EntityName.mealType)
        let persisted = try context.fetch(request)

        XCTAssertEqual(persisted.count, 5)
    }

    func test_setupDefaultMeals_assignsSequentialIdsStartingFromZero() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        let ids = result.map { $0.id }.sorted()
        XCTAssertEqual(ids, [0, 1, 2, 3, 4])
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: SetupDefaultMealsUseCase, context: NSManagedObjectContext) {
        let context = makeInMemoryContext()
        let sut = SetupDefaultMealsUseCase(context: context)
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
}
