//
//  FetchFoodsConsumedUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
import CoreData
@testable import Kalorie

final class FetchFoodsConsumedUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFoodsConsumed_withEmptyContext_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(for: .now)
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchFoodsConsumed_withPersistedItems_returnsAll() async throws {
        let (sut, context) = makeSUT()
        _ = FoodConsumed(id: "1", name: "Vejce", weight: 100, date: Date.now.timeIntervalSince1970, calories: 150, mealType: nil, context: context)
        _ = FoodConsumed(id: "2", name: "Chléb", weight: 50, date: Date.now.timeIntervalSince1970, calories: 120, mealType: nil, context: context)
        try context.save()

        let result = try await sut(for: .now)

        XCTAssertEqual(result.count, 2)
        XCTAssertTrue(result.contains(where: { $0.name == "Vejce" }))
        XCTAssertTrue(result.contains(where: { $0.name == "Chléb" }))
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchFoodsConsumedUseCase, context: NSManagedObjectContext) {
        let context = makeInMemoryContext()
        let sut = FetchFoodsConsumedUseCase(context: context)
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
