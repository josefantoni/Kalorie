//
//  DeleteMySubmissionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 15.09.2026.
//

import XCTest
@testable import Kalorie

final class DeleteMySubmissionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_deleteMySubmission_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut(id: "sub-1")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertNil(dataProvider.deletedId)
    }

    func test_deleteMySubmission_deletesFromFoodItemSubmissionsCollection() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        try await sut(id: "sub-1")
        XCTAssertEqual(dataProvider.deletedFromCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.deletedId, "sub-1")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: DeleteMySubmissionUseCase, dataProvider: DeleteMySubmissionDataProviderFake) {
        let dataProvider = DeleteMySubmissionDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = DeleteMySubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class DeleteMySubmissionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var deletedFromCollection: String?
    var deletedId: String?

    // MARK: - Functions

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

    func deleteAsync(id: String, from collection: String) async throws {
        deletedId = id
        deletedFromCollection = collection
    }
}
