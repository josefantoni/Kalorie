//
//  FetchFoodsConsumedForMonthUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodsConsumedForMonthUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFoodsConsumedForMonth_withEmptyProvider_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(for: .now)
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchFoodsConsumedForMonth_withStubbedItemsInMonth_returnsAll() async throws {
        let (sut, dataProvider) = makeSUT()
        let march = try makeDate(year: 2026, month: 3, day: 15)
        dataProvider.stubbedDTOs = [
            makeDTO(id: "1", czName: "Vejce", date: march),
            makeDTO(id: "2", czName: "Chléb", date: try makeDate(year: 2026, month: 3, day: 20))
        ]

        let result = try await sut(for: march)

        XCTAssertEqual(result.count, 2)
        XCTAssertTrue(result.contains { $0.czName == "Vejce" })
        XCTAssertTrue(result.contains { $0.czName == "Chléb" })
    }

    func test_fetchFoodsConsumedForMonth_filtersOutItemsFromPreviousAndNextMonth() async throws {
        let (sut, dataProvider) = makeSUT()
        let march = try makeDate(year: 2026, month: 3, day: 15)
        dataProvider.stubbedDTOs = [
            makeDTO(id: "1", czName: "Únorové jídlo", date: try makeDate(year: 2026, month: 2, day: 28)),
            makeDTO(id: "2", czName: "Březnové jídlo", date: march),
            makeDTO(id: "3", czName: "Dubnové jídlo", date: try makeDate(year: 2026, month: 4, day: 1))
        ]

        let result = try await sut(for: march)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].czName, "Březnové jídlo")
    }

    func test_fetchFoodsConsumedForMonth_includesStartOfMonthAndExcludesStartOfNextMonth() async throws {
        let (sut, dataProvider) = makeSUT()
        let startOfMonth = try makeDate(year: 2026, month: 3, day: 1)
        let startOfNextMonth = try makeDate(year: 2026, month: 4, day: 1)
        dataProvider.stubbedDTOs = [
            makeDTO(id: "1", czName: "První den měsíce", date: startOfMonth),
            makeDTO(id: "2", czName: "První den dalšího měsíce", date: startOfNextMonth)
        ]

        let result = try await sut(for: startOfMonth)

        // lower bound is inclusive, upper bound is exclusive — the epoch-seconds range query ADR 0008 warns about
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].czName, "První den měsíce")
    }

    func test_fetchFoodsConsumedForMonth_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(for: .now)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchFoodsConsumedForMonthUseCase, dataProvider: FetchFoodsConsumedForMonthDataProviderFake) {
        let dataProvider = FetchFoodsConsumedForMonthDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchFoodsConsumedForMonthUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeDate(year: Int, month: Int, day: Int) throws -> Date {
        try XCTUnwrap(Calendar.current.date(from: DateComponents(year: year, month: month, day: day)))
    }

    private func makeDTO(id: String, czName: String, date: Date) -> FoodConsumedDTO {
        FoodConsumedDTO(
            id: id,
            foodItemId: id,
            foodItemKind: .catalogue,
            czName: czName,
            engName: "",
            weight: 100,
            date: date.timeIntervalSince1970,
            calories: 150,
            caloriesPerHundredGrams: nil,
            energyKJ: nil,
            protein: 0,
            carbohydrate: 0,
            carbohydrateSugar: 0,
            fat: 0,
            fatSaturated: nil,
            fatUnsaturated: 0,
            fiber: 0,
            salt: 0,
            mealTypeId: nil
        )
    }
}

private final class FetchFoodsConsumedForMonthDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FoodConsumedDTO] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
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
