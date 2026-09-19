//
//  SubmitFoodItemReportUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 17.09.2026.
//

import XCTest
@testable import Kalorie

final class SubmitFoodItemReportUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_submitFoodItemReport_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, dataProvider) = makeSUT(userId: nil)
        do {
            try await sut(barcode: "12345678", reason: "wrong calories")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertNil(dataProvider.savedId)
    }

    func test_submitFoodItemReport_withEmptyReason_throwsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut(barcode: "12345678", reason: "   ")
            XCTFail("Expected reasonRequired error")
        } catch FoodItemReportError.reasonRequired {}
        XCTAssertNil(dataProvider.savedId, "a report with no text is a downvote the maintainer cannot act on")
    }

    func test_submitFoodItemReport_withReasonOverLimit_throwsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let tooLong = String(repeating: "a", count: Constants.Firestore.reportReasonMaxLength + 1)
        do {
            try await sut(barcode: "12345678", reason: tooLong)
            XCTFail("Expected reasonTooLong error")
        } catch FoodItemReportError.reasonTooLong {}
        XCTAssertNil(dataProvider.savedId)
    }

    func test_submitFoodItemReport_withReasonOverLimitInUTF16Units_throwsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let tooLong = String(repeating: "😀", count: Constants.Firestore.reportReasonMaxLength)
        do {
            try await sut(barcode: "12345678", reason: tooLong)
            XCTFail("Expected reasonTooLong error")
        } catch FoodItemReportError.reasonTooLong {}
        XCTAssertNil(
            dataProvider.savedId,
            "the rules count UTF-16 units, so a reason within the limit in Characters is still refused server-side"
        )
    }

    func test_submitFoodItemReport_withDiacriticsAtLimit_writes() async throws {
        let (sut, dataProvider) = makeSUT()
        let atLimit = String(repeating: "ě", count: Constants.Firestore.reportReasonMaxLength)
        try await sut(barcode: "12345678", reason: atLimit)
        XCTAssertNotNil(dataProvider.savedId, "the rules accept this, so a Czech reason must not be cut short by counting bytes")
    }

    func test_submitFoodItemReport_writesUnderTheComposedBarcodeAndUserIdKey() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        try await sut(barcode: "12345678", reason: "the fat is wrong")
        XCTAssertEqual(
            dataProvider.savedId,
            "12345678_user-42",
            "the rules verify this exact composition — a mismatch fails every write with permissionDenied"
        )
        XCTAssertEqual(dataProvider.savedCollection, Constants.Firestore.foodItemReports)
    }

    func test_submitFoodItemReport_trimsWhitespaceFromReason() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(barcode: "12345678", reason: "  the fat is wrong  ")
        XCTAssertEqual(dataProvider.savedReport?.reason, "the fat is wrong")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: SubmitFoodItemReportUseCase, dataProvider: SubmitFoodItemReportDataProviderFake) {
        let dataProvider = SubmitFoodItemReportDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = SubmitFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }
}

private final class SubmitFoodItemReportDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var savedId: String?
    var savedCollection: String?
    var savedReport: FoodItemReportDTO?

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

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        savedId = id
        savedCollection = collection
        savedReport = item as? FoodItemReportDTO
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
