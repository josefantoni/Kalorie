//
//  FetchMyFoodItemReportUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 17.09.2026.
//

import XCTest
@testable import Kalorie

final class FetchMyFoodItemReportUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchMyFoodItemReport_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(barcode: "12345678")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetchMyFoodItemReport_readsByTheComposedBarcodeAndUserIdKey() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        _ = try await sut(barcode: "12345678")
        XCTAssertEqual(dataProvider.queriedId, "12345678_user-42")
        XCTAssertEqual(dataProvider.queriedCollection, Constants.Firestore.foodItemReports)
    }

    func test_fetchMyFoodItemReport_whenNoDocument_returnsNil() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(barcode: "12345678")
        XCTAssertNil(result)
    }

    func test_fetchMyFoodItemReport_whenDocumentExists_returnsIt() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        dataProvider.stubbedDTO = FoodItemReportDTO(barcode: "12345678", reportedBy: "user-42", reason: "wrong fat", reportedAt: .now)
        let result = try await sut(barcode: "12345678")
        XCTAssertEqual(result?.reason, "wrong fat")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchMyFoodItemReportUseCase, dataProvider: FetchMyFoodItemReportDataProviderFake) {
        let dataProvider = FetchMyFoodItemReportDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = FetchMyFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class FetchMyFoodItemReportDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedDTO: FoodItemReportDTO?
    var queriedId: String?
    var queriedCollection: String?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        queriedId = id
        queriedCollection = collection
        return stubbedDTO as? T
    }

    func loadAsync<T: Decodable>(from collection: String, whereDocumentIdIn ids: [String]) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
