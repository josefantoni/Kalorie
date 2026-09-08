//
//  UpdateMealTypeTimesUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.09.2026.
//

import XCTest
@testable import Kalorie

final class UpdateMealTypeTimesUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_updateMealTypeTimes_writesToUserSpecificCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut([makeMealType()])
        XCTAssertEqual(dataProvider.batchSetCollection, "users/user-123/mealTypes")
    }

    func test_updateMealTypeTimes_convertsTimesToMinutesSinceMidnight() async throws {
        let (sut, dataProvider) = makeSUT()
        let mealType = makeMealType(startTime: makeDate(hour: 7, minute: 30), endTime: makeDate(hour: 9, minute: 0))

        try await sut([mealType])

        XCTAssertEqual(dataProvider.batchSetItems.first?.item.startMinutes, 450)
        XCTAssertEqual(dataProvider.batchSetItems.first?.item.endMinutes, 540)
    }

    func test_updateMealTypeTimes_preservesOrderAndCount() async throws {
        let (sut, dataProvider) = makeSUT()
        let mealTypes = [
            makeMealType(id: "breakfast"),
            makeMealType(id: "lunch"),
            makeMealType(id: "dinner")
        ]

        try await sut(mealTypes)

        XCTAssertEqual(dataProvider.batchSetItems.map { $0.id }, ["breakfast", "lunch", "dinner"])
    }

    func test_updateMealTypeTimes_preservesIdAndName() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut([makeMealType(id: "lunch", name: "Oběd")])

        XCTAssertEqual(dataProvider.batchSetItems.first?.item.id, "lunch")
        XCTAssertEqual(dataProvider.batchSetItems.first?.item.name, "Oběd")
    }

    func test_updateMealTypeTimes_whenNotAuthenticated_throwsAuthErrorAndNeverWrites() async throws {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut([makeMealType()])
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertTrue(dataProvider.batchSetItems.isEmpty)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: UpdateMealTypeTimesUseCase, dataProvider: UpdateMealTypeTimesDataProviderFake) {
        let dataProvider = UpdateMealTypeTimesDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = UpdateMealTypeTimesUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeMealType(
        id: String = "lunch",
        name: String = "Oběd",
        startTime: Date = Date(),
        endTime: Date = Date()
    ) -> MealTypeDomain {
        MealTypeDomain(id: id, name: name, startTime: startTime, endTime: endTime)
    }

    private func makeDate(hour: Int, minute: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }
}

private final class UpdateMealTypeTimesDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var batchSetItems: [(item: MealTypeDTO, id: String)] = []
    var batchSetCollection: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        batchSetItems = items.compactMap { pair in (pair.item as? MealTypeDTO).map { (item: $0, id: pair.id) } }
        batchSetCollection = collection
    }

    func deleteAsync(id: String, from collection: String) async throws {}
}
