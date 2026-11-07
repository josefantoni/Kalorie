//
//  FetchPhotoSuggestionsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class FetchPhotoSuggestionsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchPhotoSuggestions_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetchPhotoSuggestions_fetchesTheOldestPageFirst_soTheLongestWaitingSuggestionIsNeverHiddenByTheLimit() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, Constants.Firestore.photoSuggestions)
        XCTAssertEqual(dataProvider.queriedOrderField, Constants.Firestore.Field.suggestedAt)
        XCTAssertEqual(dataProvider.queriedDescending, false)
        XCTAssertEqual(dataProvider.queriedLimit, Constants.Firestore.reportsPageLimit)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: FetchPhotoSuggestionsUseCase, dataProvider: FetchPhotoSuggestionsDataProviderFake) {
        let dataProvider = FetchPhotoSuggestionsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchPhotoSuggestionsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class FetchPhotoSuggestionsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [PhotoSuggestionDTO] = []
    var queriedCollection: String?
    var queriedOrderField: String?
    var queriedDescending: Bool?
    var queriedLimit: Int?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, whereDocumentIdIn ids: [String]) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }

    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] {
        queriedCollection = collection
        queriedOrderField = field
        queriedDescending = descending
        queriedLimit = limit
        return stubbedDTOs.compactMap { $0 as? T }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
