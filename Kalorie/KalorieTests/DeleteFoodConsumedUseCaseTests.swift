//
//  DeleteFoodConsumedUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 29.08.2026.
//

import XCTest
@testable import Kalorie

final class DeleteFoodConsumedUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_deleteFoodConsumed_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(id: "food-1")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_deleteFoodConsumed_deletesFromUserSpecificCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(id: "food-1")
        XCTAssertEqual(dataProvider.deletedFromCollection, "users/user-123/foodConsumed")
        XCTAssertEqual(dataProvider.deletedId, "food-1")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: DeleteFoodConsumedUseCase, dataProvider: DeleteFoodConsumedDataProviderFake) {
        let dataProvider = DeleteFoodConsumedDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = DeleteFoodConsumedUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class DeleteFoodConsumedDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var deletedFromCollection: String?
    var deletedId: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}

    func deleteAsync(id: String, from collection: String) async throws {
        deletedId = id
        deletedFromCollection = collection
    }
}
