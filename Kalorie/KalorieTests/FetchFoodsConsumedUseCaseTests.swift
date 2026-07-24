//
//  FetchFoodsConsumedUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodsConsumedUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFoodsConsumed_withEmptyProvider_returnsEmptyArray() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(for: .now)
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchFoodsConsumed_withStubbedItems_returnsAll() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [
            FoodConsumedDTO(id: "1", czName: "Vejce", engName: "Egg", weight: 100, date: Date.now.timeIntervalSince1970, calories: 150),
            FoodConsumedDTO(id: "2", czName: "Chléb", engName: "", weight: 50, date: Date.now.timeIntervalSince1970, calories: 120)
        ]

        let result = try await sut(for: .now)

        XCTAssertEqual(result.count, 2)
        XCTAssertTrue(result.contains { $0.czName == "Vejce" })
        XCTAssertTrue(result.contains { $0.czName == "Chléb" })
    }

    func test_fetchFoodsConsumed_filtersOutItemsFromDifferentDay() async throws {
        let (sut, dataProvider) = makeSUT()
        let yesterday = try XCTUnwrap(Calendar.current.date(byAdding: .day, value: -1, to: .now))
        dataProvider.stubbedDTOs = [
            FoodConsumedDTO(id: "1", czName: "Vejce", engName: "Egg", weight: 100, date: Date.now.timeIntervalSince1970, calories: 150),
            FoodConsumedDTO(id: "2", czName: "Včerejší chléb", engName: "", weight: 50, date: yesterday.timeIntervalSince1970, calories: 120)
        ]

        let result = try await sut(for: .now)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].czName, "Vejce")
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchFoodsConsumedUseCase, dataProvider: FetchFoodsConsumedDataProviderFake) {
        let dataProvider = FetchFoodsConsumedDataProviderFake()
        let sut = FetchFoodsConsumedUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake())
        return (sut, dataProvider)
    }
}

private final class FetchFoodsConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FoodConsumedDTO] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        stubbedDTOs.compactMap { $0 as? T }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }

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
