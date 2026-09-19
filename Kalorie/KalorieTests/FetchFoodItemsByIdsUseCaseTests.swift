//
//  FetchFoodItemsByIdsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 19.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodItemsByIdsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetch_queriesTheCatalogueCollection() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut(ids: ["1"])
        XCTAssertEqual(dataProvider.queriedCollection, "foodItems")
    }

    func test_fetch_deduplicatesIdsBeforeQuerying() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut(ids: ["1", "1", "2"])
        XCTAssertEqual(Set(dataProvider.queriedIds), ["1", "2"])
        XCTAssertEqual(dataProvider.queriedIds.count, 2)
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchFoodItemsByIdsUseCase, dataProvider: FetchByIdsDataProviderFake) {
        let dataProvider = FetchByIdsDataProviderFake()
        return (FetchFoodItemsByIdsUseCase(dataProvider: dataProvider), dataProvider)
    }
}

private final class FetchByIdsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private(set) var queriedCollection: String?
    private(set) var queriedIds: [String] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String, whereDocumentIdIn ids: [String]) async throws -> [T] {
        queriedCollection = collection
        queriedIds = ids
        return []
    }

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
