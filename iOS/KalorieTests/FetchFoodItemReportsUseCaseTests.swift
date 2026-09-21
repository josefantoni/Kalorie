//
//  FetchFoodItemReportsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 17.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodItemReportsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchFoodItemReports_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetchFoodItemReports_queriesWholeCollectionOrderedByReportedAtDescending() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        XCTAssertEqual(dataProvider.queriedCollection, Constants.Firestore.foodItemReports)
        XCTAssertEqual(dataProvider.queriedOrderField, "reported_at")
        XCTAssertEqual(dataProvider.queriedDescending, true)
        XCTAssertEqual(dataProvider.queriedLimit, Constants.Firestore.reportsPageLimit)
    }

    func test_fetchFoodItemReports_mapsDTOsToDomain() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDTOs = [FoodItemReportDTO(barcode: "12345678", reportedBy: "user-1", reason: "wrong fat", reportedAt: .now)]
        let result = try await sut()
        XCTAssertEqual(result.map(\.barcode), ["12345678"])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: FetchFoodItemReportsUseCase, dataProvider: FetchFoodItemReportsDataProviderFake) {
        let dataProvider = FetchFoodItemReportsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchFoodItemReportsUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class FetchFoodItemReportsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTOs: [FoodItemReportDTO] = []
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
