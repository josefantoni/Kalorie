//
//  FetchPendingSubmissionsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchPendingSubmissionsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchPendingSubmissions_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_fetchPendingSubmissions_queriesPendingStatusOrderedBySubmittedAtDescending() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.queriedField, "status")
        XCTAssertEqual(dataProvider.queriedValue, FoodItemSubmissionStatus.pending.rawValue)
        XCTAssertEqual(dataProvider.queriedOrderField, "submitted_at")
        XCTAssertEqual(dataProvider.queriedDescending, true)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: FetchPendingSubmissionsUseCase, dataProvider: FetchPendingSubmissionsDataProviderFake) {
        let dataProvider = FetchPendingSubmissionsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchPendingSubmissionsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class FetchPendingSubmissionsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FoodItemSubmissionDTO] = []
    var queriedCollection: String?
    var queriedField: String?
    var queriedValue: String?
    var queriedOrderField: String?
    var queriedDescending: Bool?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] {
        queriedCollection = collection
        queriedField = field
        queriedValue = value
        queriedOrderField = orderField
        queriedDescending = descending
        return stubbedDTOs.compactMap { $0 as? T }
    }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
