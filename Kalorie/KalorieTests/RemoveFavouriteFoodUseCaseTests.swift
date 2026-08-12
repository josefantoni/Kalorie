//
//  RemoveFavouriteFoodUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class RemoveFavouriteFoodUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_removeFavouriteFood_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(id: "12345")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_removeFavouriteFood_deletesFromUserSpecificCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(id: "12345")
        XCTAssertEqual(dataProvider.deletedId, "12345")
        XCTAssertEqual(dataProvider.deletedFromCollection, "users/user-123/favouriteFoods")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: RemoveFavouriteFoodUseCase, dataProvider: RemoveFavouriteFoodDataProviderFake) {
        let dataProvider = RemoveFavouriteFoodDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = RemoveFavouriteFoodUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class RemoveFavouriteFoodDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var deletedId: String?
    var deletedFromCollection: String?

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
