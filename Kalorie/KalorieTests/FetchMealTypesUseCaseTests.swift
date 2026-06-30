//
//  FetchMealTypesUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
import CoreData
@testable import Kalorie

final class FetchMealTypesUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchMealTypes_withEmptyContext_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchMealTypes_withPersistedMealTypes_returnsAll() async throws {
        let (sut, context) = makeSUT()
        _ = MealType(id: 0, name: "Snídaně", startTime: makeDate(hour: 6), endTime: makeDate(hour: 9), context: context)
        _ = MealType(id: 1, name: "Oběd", startTime: makeDate(hour: 12), endTime: makeDate(hour: 14), context: context)
        try context.save()

        let result = try await sut()

        XCTAssertEqual(result.count, 2)
        XCTAssertTrue(result.contains(where: { $0.name == "Snídaně" }))
        XCTAssertTrue(result.contains(where: { $0.name == "Oběd" }))
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchMealTypesUseCase, context: NSManagedObjectContext) {
        let context = makeInMemoryContext()
        let sut = FetchMealTypesUseCase(context: context)
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
