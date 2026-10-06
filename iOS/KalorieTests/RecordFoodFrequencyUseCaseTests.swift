//
//  RecordFoodFrequencyUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 06.10.2026.
//

import XCTest
@testable import Kalorie

final class RecordFoodFrequencyUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_recordFoodFrequency_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut(makeItem(id: "12345678"), date: .now)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            XCTAssertNil(dataProvider.recordedEntryId)
        }
    }

    func test_recordFoodFrequency_incrementsTheEntryKeyedByFoodIdInTheUserStatsDocument() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        let date = Date(timeIntervalSince1970: 1_700_000_000)

        try await sut(makeItem(id: "8594000123456"), date: date)

        XCTAssertEqual(dataProvider.recordedEntryId, "8594000123456", "the key must be the food id, the same value as food_item_id on foodConsumed")
        XCTAssertEqual(dataProvider.recordedCollection, "users/user-123/stats")
        XCTAssertEqual(dataProvider.recordedDocumentId, "foodFrequency")
        XCTAssertEqual(dataProvider.recordedLastLoggedAt, 1_700_000_000)
    }

    func test_recordFoodFrequency_storesTheFullItemSnapshotWithoutFavouriteOnlyFields() async throws {
        let (sut, dataProvider) = makeSUT()

        try await sut(makeItem(id: "1", czName: "Tvaroh"), date: .now)

        let snapshot = try XCTUnwrap(dataProvider.recordedItem)
        XCTAssertEqual(snapshot.id, "1")
        XCTAssertEqual(snapshot.czName, "Tvaroh")
        XCTAssertEqual(snapshot.foodItemKind, .catalogue)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(snapshot)) as? [String: Any])
        XCTAssertNil(json["favourited_at"])
        XCTAssertNotNil(json["food_item_kind"])
    }

    func test_recordFoodFrequency_forACreatedMeal_incrementsTheEntryKeyedByTheMealId() async throws {
        let (sut, dataProvider) = makeSUT()

        try await sut(makeItem(id: "meal-1", kind: .createdMeal), date: .now)

        XCTAssertEqual(dataProvider.recordedEntryId, "meal-1", "a created meal must be counted so it can outrank a once-logged catalogue food")
        XCTAssertEqual(dataProvider.recordedItem?.foodItemKind, .createdMeal)
    }

    func test_recordFoodFrequency_whenProviderThrows_propagatesTheError() async {
        let (sut, dataProvider) = makeSUT()
        dataProvider.shouldThrow = true
        do {
            try await sut(makeItem(id: "1"), date: .now)
            XCTFail("Expected the provider error")
        } catch {
            XCTAssertTrue(error is URLError)
        }
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: RecordFoodFrequencyUseCase, dataProvider: RecordFoodFrequencyDataProviderFake) {
        let dataProvider = RecordFoodFrequencyDataProviderFake()
        let sut = RecordFoodFrequencyUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }

    private func makeItem(id: String, czName: String = "Tvaroh", kind: FoodItemKind = .catalogue) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: kind,
            czName: czName,
            engName: "",
            weight: 100,
            date: .now,
            energyKJ: 0,
            caloriesPerHundredGrams: 100,
            fat: 0,
            fatSaturated: 0,
            fatUnsaturatedFattyAcids: 0,
            carbohydrate: 0,
            carbohydratePureSugar: 0,
            fiber: 0,
            protein: 0,
            salt: 0
        )
    }
}

private final class RecordFoodFrequencyDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var shouldThrow = false
    private(set) var recordedItem: FoodFrequencyItemDTO?
    private(set) var recordedEntryId: String?
    private(set) var recordedLastLoggedAt: TimeInterval?
    private(set) var recordedDocumentId: String?
    private(set) var recordedCollection: String?

    // MARK: - Functions

    func incrementEntryAsync<T: Encodable>(_ item: T, entryId: String, lastLoggedAt: TimeInterval, documentId: String, in collection: String) async throws {
        if shouldThrow { throw URLError(.unknown) }
        recordedItem = item as? FoodFrequencyItemDTO
        recordedEntryId = entryId
        recordedLastLoggedAt = lastLoggedAt
        recordedDocumentId = documentId
        recordedCollection = collection
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
