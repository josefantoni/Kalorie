//
//  FetchFoodFrequencyUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 06.10.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodFrequencyUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFoodFrequency_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_fetchFoodFrequency_readsTheSingleStatsDocument() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        _ = try await sut()
        XCTAssertEqual(dataProvider.loadedCollection, "users/user-123/stats")
        XCTAssertEqual(dataProvider.loadedId, "foodFrequency")
    }

    func test_fetchFoodFrequency_whenDocumentMissing_returnsEmptyMap() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut()
        XCTAssertTrue(result.isEmpty)
    }

    func test_fetchFoodFrequency_mapsEntriesByFoodId() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = try makeDocument(entries: ["a": entryJSON(id: "a", count: 3, lastLoggedAt: 100)])

        let result = try await sut()

        XCTAssertEqual(result["a"]?.count, 3)
        XCTAssertEqual(result["a"]?.lastLoggedAt, Date(timeIntervalSince1970: 100))
        XCTAssertEqual(result["a"]?.item.id, "a")
    }

    func test_fetchFoodFrequency_dropsAnEntryWithoutDecodableItemInsteadOfFailingTheWholeDocument() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = try makeDocument(entries: [
            "good": entryJSON(id: "good", count: 1, lastLoggedAt: 1),
            "broken": ["count": 5, "last_logged_at": 1.0]
        ])

        let result = try await sut()

        XCTAssertEqual(Set(result.keys), ["good"])
    }

    func test_fetchFoodFrequency_withAnEntryWithoutDecodableItem_deletesItSoItDoesNotCountTowardTheSizeLimit() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = try makeDocument(entries: [
            "good": entryJSON(id: "good", count: 1, lastLoggedAt: 1),
            "broken": ["count": 5, "last_logged_at": 1.0]
        ])

        _ = try await sut()

        XCTAssertEqual(dataProvider.deletedEntryIds, ["broken"])
    }

    func test_fetchFoodFrequency_withMoreThanTheLimit_trimsTheLeastRecentlyLoggedAndDeletesTheRest() async throws {
        let (sut, dataProvider) = makeSUT()
        let limit = Constants.Search.frequencyEntryLimit
        var entries: [String: Any] = [:]
        for index in 0..<limit {
            entries["id-\(index)"] = entryJSON(id: "id-\(index)", count: 2, lastLoggedAt: 100 + Double(index))
        }
        entries["old-frequent"] = entryJSON(id: "old-frequent", count: 50, lastLoggedAt: 1)
        entries["old-rare"] = entryJSON(id: "old-rare", count: 1, lastLoggedAt: 2)
        dataProvider.stubbedDocument = try makeDocument(entries: entries)

        let result = try await sut()

        XCTAssertEqual(result.count, limit)
        XCTAssertNil(result["old-frequent"])
        XCTAssertNil(result["old-rare"])
        XCTAssertEqual(Set(dataProvider.deletedEntryIds), ["old-frequent", "old-rare"])
    }

    func test_fetchFoodFrequency_withAFullTable_keepsAFoodLoggedForTheFirstTime() async throws {
        let (sut, dataProvider) = makeSUT()
        let limit = Constants.Search.frequencyEntryLimit
        var entries: [String: Any] = [:]
        for index in 0..<limit {
            entries["id-\(index)"] = entryJSON(id: "id-\(index)", count: 5, lastLoggedAt: 100 + Double(index))
        }
        entries["fresh"] = entryJSON(id: "fresh", count: 1, lastLoggedAt: 1_000)
        dataProvider.stubbedDocument = try makeDocument(entries: entries)

        let result = try await sut()

        XCTAssertEqual(result["fresh"]?.count, 1, "a count of 1 must not make a just-logged food the first to go, or it could never reach 2")
        XCTAssertNil(result["id-0"])
    }

    func test_fetchFoodFrequency_whenTrimFails_stillReturnsTheTrimmedMap() async throws {
        let (sut, dataProvider) = makeSUT()
        let limit = Constants.Search.frequencyEntryLimit
        var entries: [String: Any] = [:]
        for index in 0..<(limit + 1) {
            entries["id-\(index)"] = entryJSON(id: "id-\(index)", count: index + 1, lastLoggedAt: 1)
        }
        dataProvider.stubbedDocument = try makeDocument(entries: entries)
        dataProvider.shouldThrowOnDelete = true

        let result = try await sut()

        XCTAssertEqual(result.count, limit)
        XCTAssertNil(result["id-0"])
    }

    func test_fetchFoodFrequency_withinTheLimit_deletesNothing() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocument = try makeDocument(entries: ["a": entryJSON(id: "a", count: 1, lastLoggedAt: 1)])
        _ = try await sut()
        XCTAssertTrue(dataProvider.deletedEntryIds.isEmpty)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchFoodFrequencyUseCase, dataProvider: FetchFoodFrequencyDataProviderFake) {
        let dataProvider = FetchFoodFrequencyDataProviderFake()
        let sut = FetchFoodFrequencyUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }

    private func entryJSON(id: String, count: Int, lastLoggedAt: Double) -> [String: Any] {
        [
            "count": count,
            "last_logged_at": lastLoggedAt,
            "item": [
                "id": id,
                "food_item_kind": "catalogue",
                "cz_name": "Tvaroh",
                "eng_name": "",
                "weight": 100.0,
                "date": 0.0,
                "calories_per_hundred_grams": 100.0,
                "fat": 0.0,
                "fat_unsaturated_fatty_acids": 0.0,
                "carbohydrate": 0.0,
                "carbohydrate_pure_sugar": 0.0,
                "protein": 0.0,
                "salt": 0.0
            ] as [String: Any]
        ]
    }

    private func makeDocument(entries: [String: Any]) throws -> FoodFrequencyDocumentDTO {
        let data = try JSONSerialization.data(withJSONObject: ["entries": entries])
        return try JSONDecoder().decode(FoodFrequencyDocumentDTO.self, from: data)
    }
}

private final class FetchFoodFrequencyDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDocument: FoodFrequencyDocumentDTO?
    var shouldThrowOnDelete = false
    private(set) var loadedCollection: String?
    private(set) var loadedId: String?
    private(set) var deletedEntryIds: [String] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        loadedCollection = collection
        loadedId = id
        return stubbedDocument as? T
    }

    func deleteEntriesAsync(ids: [String], documentId: String, in collection: String) async throws {
        if shouldThrowOnDelete { throw URLError(.unknown) }
        deletedEntryIds = ids
    }

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
