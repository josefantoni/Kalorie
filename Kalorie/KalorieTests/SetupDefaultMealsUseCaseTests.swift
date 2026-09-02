//
//  SetupDefaultMealsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
@testable import Kalorie

final class SetupDefaultMealsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_setupDefaultMeals_returnsExactlyFiveMealTypes() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertEqual(result.count, 5)
    }

    func test_setupDefaultMeals_persistsFiveMealTypesToDataProvider() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        XCTAssertEqual(dataProvider.batchSavedCount, 5)
    }

    func test_setupDefaultMeals_assignsDistinctNonEmptyIds() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        let ids = Set(result.map { $0.id })
        XCTAssertEqual(ids.count, 5, "each default meal must get its own id, or setAsync would silently overwrite one with another")
        XCTAssertTrue(ids.allSatisfy { !$0.isEmpty })
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: SetupDefaultMealsUseCase, dataProvider: SetupDefaultMealsDataProviderFake) {
        let dataProvider = SetupDefaultMealsDataProviderFake()
        let sut = SetupDefaultMealsUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake())
        return (sut, dataProvider)
    }
}

private final class SetupDefaultMealsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var batchSavedCount = 0

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        batchSavedCount = items.count
    }
    func deleteAsync(id: String, from collection: String) async throws {}
}
