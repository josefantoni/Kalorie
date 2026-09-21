//
//  FetchMySubmissionsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchMySubmissionsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchMySubmissions_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_fetchMySubmissions_queriesOwnSubmissionsOrderedBySubmittedAtDescending() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-123")
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.queriedField, "submitted_by")
        XCTAssertEqual(dataProvider.queriedValue, "user-123")
        XCTAssertEqual(dataProvider.queriedOrderField, "submitted_at")
        XCTAssertEqual(dataProvider.queriedDescending, true)
    }

    func test_fetchMySubmissions_mapsStubbedDTOsToDomains() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [makeDTO(id: "sub-1", barcode: "12345678")]
        let result = try await sut()
        XCTAssertEqual(result.map(\.id), ["sub-1"])
        XCTAssertEqual(result.map(\.barcode), ["12345678"])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchMySubmissionsUseCase, dataProvider: FetchMySubmissionsDataProviderFake) {
        let dataProvider = FetchMySubmissionsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchMySubmissionsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeDTO(id: String, barcode: String) -> FoodItemSubmissionDTO {
        FoodItemSubmissionDTO(
            id: id,
            barcode: barcode,
            submittedBy: "test-user",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: FoodItemDomain(
                id: barcode,
                kind: .catalogue,
                czName: "Tvaroh",
                engName: "",
                weight: 200,
                date: .now,
                energyKJ: 335,
                caloriesPerHundredGrams: 80,
                fat: 0.5,
                fatSaturated: 0.3,
                fatUnsaturatedFattyAcids: 0.2,
                carbohydrate: 4,
                carbohydratePureSugar: 3,
                fiber: 0,
                protein: 13,
                salt: 0.1
            )
        )
    }
}

private final class FetchMySubmissionsDataProviderFake: FirestoreDataProviderProtocol {

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
