//
//  DeleteFoodItemReportUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 17.09.2026.
//

import XCTest
@testable import Kalorie

final class DeleteFoodItemReportUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_deleteFoodItemReport_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut(barcode: "12345678", reportedBy: "user-1")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertNil(dataProvider.deletedId)
    }

    func test_deleteFoodItemReport_deletesByTheComposedBarcodeAndReportedByKey() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(barcode: "12345678", reportedBy: "user-1")
        XCTAssertEqual(dataProvider.deletedId, "12345678_user-1")
        XCTAssertEqual(dataProvider.deletedFromCollection, Constants.Firestore.foodItemReports)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: DeleteFoodItemReportUseCase, dataProvider: DeleteFoodItemReportDataProviderFake) {
        let dataProvider = DeleteFoodItemReportDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = DeleteFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class DeleteFoodItemReportDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var deletedId: String?
    var deletedFromCollection: String?

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
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}

    func deleteAsync(id: String, from collection: String) async throws {
        deletedId = id
        deletedFromCollection = collection
    }
}
