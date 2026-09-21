//
//  FetchFoodsConsumedInRangeUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 19.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodsConsumedInRangeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchInRange_includesTheWholeLastDayAndExcludesTheNextOne() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [
            makeDTO(id: "before", date: try makeDate(day: 4, hour: 23, minute: 59)),
            makeDTO(id: "firstMorning", date: try makeDate(day: 5, hour: 0, minute: 0)),
            makeDTO(id: "lastNight", date: try makeDate(day: 7, hour: 23, minute: 59)),
            makeDTO(id: "after", date: try makeDate(day: 8, hour: 0, minute: 0))
        ]

        let result = try await sut(from: try makeDate(day: 5, hour: 15), to: try makeDate(day: 7, hour: 9))

        XCTAssertEqual(Set(result.map(\.id)), ["firstMorning", "lastNight"])
    }

    func test_fetchInRange_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(from: .now, to: .now)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchFoodsConsumedInRangeUseCase, dataProvider: FetchInRangeDataProviderFake) {
        let dataProvider = FetchInRangeDataProviderFake()
        let sut = FetchFoodsConsumedInRangeUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }

    private func makeDate(day: Int, hour: Int = 0, minute: Int = 0) throws -> Date {
        try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour, minute: minute)))
    }

    private func makeDTO(id: String, date: Date) -> FoodConsumedDTO {
        FoodConsumedDTO(
            id: id,
            foodItemId: id,
            foodItemKind: .catalogue,
            czName: id,
            engName: "",
            weight: 100,
            date: date.timeIntervalSince1970,
            calories: 100,
            caloriesPerHundredGrams: nil,
            energyKJ: nil,
            protein: 0,
            carbohydrate: 0,
            carbohydrateSugar: 0,
            fat: 0,
            fatSaturated: nil,
            fatUnsaturated: 0,
            fiber: nil,
            salt: 0,
            mealTypeId: nil,
            measureUnit: nil
        )
    }
}

private final class FetchInRangeDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FoodConsumedDTO] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] {
        stubbedDTOs
            .filter { $0.date >= lowerBound && $0.date < upperBound }
            .compactMap { $0 as? T }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
