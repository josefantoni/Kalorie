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
            FoodConsumedDTO(id: "1", name: "Vejce", weight: 100, date: Date.now.timeIntervalSince1970, calories: 150),
            FoodConsumedDTO(id: "2", name: "Chléb", weight: 50, date: Date.now.timeIntervalSince1970, calories: 120)
        ]

        let result = try await sut(for: .now)

        XCTAssertEqual(result.count, 2)
        XCTAssertTrue(result.contains { $0.name == "Vejce" })
        XCTAssertTrue(result.contains { $0.name == "Chléb" })
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

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
