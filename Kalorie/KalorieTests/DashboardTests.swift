//
//  KalorieTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 05.06.2024.
//

import XCTest
import CoreData
@testable import Kalorie

final class CreateMealTypeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_createMealType_withEmptyName_throwsEmptyNameError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(name: "", startTime: .now, endTime: .now, existingMealTypes: [])
            XCTFail("Expected emptyName error")
        } catch CreateMealTypeError.emptyName {
            // pass
        }
    }

    func test_createMealType_withDuplicateName_throwsDuplicateNameError() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: 1,
            name: "Snídaně",
            startTime: makeDate(hour: 6, minute: 0),
            endTime: makeDate(hour: 9, minute: 0)
        )
        do {
            _ = try await sut(
                name: "Snídaně",
                startTime: makeDate(hour: 10, minute: 0),
                endTime: makeDate(hour: 11, minute: 0),
                existingMealTypes: [existing]
            )
            XCTFail("Expected duplicateName error")
        } catch CreateMealTypeError.duplicateName {
            // pass
        }
    }

    func test_createMealType_withTimeConflict_throwsTimeConflictError() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: 1,
            name: "Snídaně",
            startTime: makeDate(hour: 6, minute: 0),
            endTime: makeDate(hour: 9, minute: 0)
        )
        do {
            _ = try await sut(
                name: "Druhá snídaně",
                startTime: makeDate(hour: 7, minute: 0),
                endTime: makeDate(hour: 8, minute: 0),
                existingMealTypes: [existing]
            )
            XCTFail("Expected timeConflict error")
        } catch CreateMealTypeError.timeConflict {
            // pass
        }
    }

    func test_createMealType_withValidInput_persistsAndReturnsMealType() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: 1,
            name: "Snídaně",
            startTime: makeDate(hour: 6, minute: 0),
            endTime: makeDate(hour: 9, minute: 0)
        )
        let result = try await sut(
            name: "Oběd",
            startTime: makeDate(hour: 11, minute: 0),
            endTime: makeDate(hour: 13, minute: 0),
            existingMealTypes: [existing]
        )
        XCTAssertEqual(result.name, "Oběd")
        XCTAssertEqual(result.id, 2)
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: CreateMealTypeUseCase, context: NSManagedObjectContext) {
        let context = makeInMemoryContext()
        let sut = CreateMealTypeUseCase(context: context)
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

    private func makeDate(hour: Int, minute: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }
}
