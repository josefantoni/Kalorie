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

    func test_setupDefaultMeals_assignsSequentialIdsStartingFromZero() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        let ids = result.map { $0.id }.sorted()
        XCTAssertEqual(ids, [0, 1, 2, 3, 4])
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
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        batchSavedCount = items.count
    }
    func deleteAsync(id: String, from collection: String) async throws {}
}
