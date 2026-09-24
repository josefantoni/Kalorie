//
//  CreateMealTypeUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 05.06.2024.
//

import XCTest
@testable import Kalorie

final class CreateMealTypeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_createMealType_withEmptyName_throwsEmptyNameError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(name: "", startMinutes: 0, endMinutes: 0, existingMealTypes: [])
            XCTFail("Expected emptyName error")
        } catch CreateMealTypeError.emptyName {
            // pass
        }
    }

    func test_createMealType_withDuplicateName_throwsDuplicateNameError() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: "1",
            name: "Snídaně",
            startMinutes: 360,
            endMinutes: 540
        )
        do {
            _ = try await sut(
                name: "Snídaně",
                startMinutes: 600,
                endMinutes: 660,
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
            id: "1",
            name: "Snídaně",
            startMinutes: 360,
            endMinutes: 540
        )
        do {
            _ = try await sut(
                name: "Druhá snídaně",
                startMinutes: 420,
                endMinutes: 480,
                existingMealTypes: [existing]
            )
            XCTFail("Expected timeConflict error")
        } catch CreateMealTypeError.timeConflict {
            // pass
        }
    }

    func test_createMealType_wrappingExistingSlot_throwsTimeConflictError() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: "1",
            name: "Snídaně",
            startMinutes: 540,
            endMinutes: 720
        )
        do {
            _ = try await sut(
                name: "Mega snídaně",
                startMinutes: 420,
                endMinutes: 840,
                existingMealTypes: [existing]
            )
            XCTFail("Expected timeConflict error")
        } catch CreateMealTypeError.timeConflict {
            // pass
        }
    }

    func test_createMealType_wrappingMidnight_isNotRejectedAsTooShort() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(
            name: "Půlnoční svačina",
            startMinutes: 1430,
            endMinutes: 20,
            existingMealTypes: []
        )
        XCTAssertEqual(result.name, "Půlnoční svačina")
    }

    func test_createMealType_withValidInput_persistsAndReturnsMealType() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: "1",
            name: "Snídaně",
            startMinutes: 360,
            endMinutes: 540
        )
        let result = try await sut(
            name: "Oběd",
            startMinutes: 660,
            endMinutes: 780,
            existingMealTypes: [existing]
        )
        XCTAssertEqual(result.name, "Oběd")
        XCTAssertFalse(result.id.isEmpty)
        XCTAssertNotEqual(result.id, existing.id, "a new meal type must never reuse an id already in use, since Firestore's setAsync would silently overwrite that document")
    }

    func test_createMealType_calledTwiceFromSameExistingSnapshot_assignsDistinctIds() async throws {
        let (sut, _) = makeSUT()
        let first = try await sut(
            name: "Snídaně",
            startMinutes: 360,
            endMinutes: 540,
            existingMealTypes: []
        )
        let second = try await sut(
            name: "Oběd",
            startMinutes: 660,
            endMinutes: 780,
            existingMealTypes: []
        )
        XCTAssertNotEqual(first.id, second.id, "two devices creating a meal type from the same stale snapshot must not collide on id and silently overwrite each other")
    }

    func test_createMealType_withWhitespaceOnlyName_throwsEmptyNameError() async throws {
        let (sut, _) = makeSUT()
        do {
            _ = try await sut(
                name: " \n ",
                startMinutes: 600,
                endMinutes: 660,
                existingMealTypes: []
            )
            XCTFail("Expected emptyName error")
        } catch CreateMealTypeError.emptyName {
            // pass
        }
    }

    func test_createMealType_withNameDifferingOnlyInCaseAndPadding_throwsDuplicateNameError() async throws {
        let (sut, _) = makeSUT()
        let existing = MealTypeDomain(
            id: "1",
            name: "Snídaně",
            startMinutes: 360,
            endMinutes: 540
        )
        do {
            _ = try await sut(
                name: "  SNÍDANĚ ",
                startMinutes: 600,
                endMinutes: 660,
                existingMealTypes: [existing]
            )
            XCTFail("Expected duplicateName error")
        } catch CreateMealTypeError.duplicateName {
            // pass
        }
    }

    func test_createMealType_withPaddedName_returnsTrimmedName() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(
            name: "  Oběd ",
            startMinutes: 660,
            endMinutes: 780,
            existingMealTypes: []
        )
        XCTAssertEqual(result.name, "Oběd")
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: CreateMealTypeUseCase, dataProvider: FirestoreDataProviderFake) {
        let dataProvider = FirestoreDataProviderFake()
        let sut = CreateMealTypeUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake())
        return (sut, dataProvider)
    }
}
