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
        XCTAssertTrue(dataProvider.sets.isEmpty)
    }

    func test_submitFoodItemReport_withEmptyReason_throwsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut(barcode: "12345678", reason: "   ")
            XCTFail("Expected reasonRequired error")
        } catch FoodItemReportError.reasonRequired {}
        XCTAssertTrue(dataProvider.sets.isEmpty, "a report with no text is a downvote the maintainer cannot act on")
    }

    func test_submitFoodItemReport_withReasonOverLimit_throwsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let tooLong = String(repeating: "a", count: Constants.Firestore.reportReasonMaxLength + 1)
        do {
            try await sut(barcode: "12345678", reason: tooLong)
            XCTFail("Expected reasonTooLong error")
        } catch FoodItemReportError.reasonTooLong {}
        XCTAssertTrue(dataProvider.sets.isEmpty)
    }

    func test_submitFoodItemReport_withReasonOverLimitInUTF16Units_throwsAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        let tooLong = String(repeating: "😀", count: Constants.Firestore.reportReasonMaxLength)
        do {
            try await sut(barcode: "12345678", reason: tooLong)
            XCTFail("Expected reasonTooLong error")
        } catch FoodItemReportError.reasonTooLong {}
        XCTAssertTrue(
            dataProvider.sets.isEmpty,
            "the rules count UTF-16 units, so a reason within the limit in Characters is still refused server-side"
        )
    }

    func test_submitFoodItemReport_withDiacriticsAtLimit_writes() async throws {
        let (sut, dataProvider) = makeSUT()
        let atLimit = String(repeating: "ě", count: Constants.Firestore.reportReasonMaxLength)
        try await sut(barcode: "12345678", reason: atLimit)
        XCTAssertFalse(dataProvider.sets.isEmpty, "the rules accept this, so a Czech reason must not be cut short by counting bytes")
    }

    func test_submitFoodItemReport_writesUnderAGeneratedIdAsAPendingVisibleTicketWithTheAppLocale() async throws {
        let (sut, dataProvider) = makeSUT(locale: "sk")
        dataProvider.newIds = ["generated-id"]
        try await sut(barcode: "12345678", reason: "the fat is wrong")
        XCTAssertEqual(dataProvider.sets.map(\.id), ["generated-id"], "neither the item nor the user may be in the id, or a kept ticket would block every later report")
        XCTAssertEqual(dataProvider.sets.first?.collection, Constants.Firestore.foodItemReports)
        let json = try encodedJSON(try XCTUnwrap(dataProvider.sets.first?.item as? FoodItemReportCreateDTO))
        XCTAssertEqual(json["status"] as? String, "pending")
        XCTAssertEqual(json["hidden"] as? Bool, false)
        XCTAssertEqual(json["locale"] as? String, "sk")
    }

    func test_submitFoodItemReport_sendsOnlyTheKeysTheCreateRuleAllows() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(barcode: "12345678", reason: "the fat is wrong")
        let json = try encodedJSON(try XCTUnwrap(dataProvider.sets.first?.item as? FoodItemReportCreateDTO))
        XCTAssertEqual(
            Set(json.keys),
            ["barcode", "reported_by", "reported_at", "reason", "status", "locale", "hidden"],
            "the rules use keys().hasOnly, so a response or seen_at key on create is denied"
        )
    }

    func test_submitFoodItemReport_twiceForTheSameItem_createsTwoTickets() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(barcode: "12345678", reason: "first")
        try await sut(barcode: "12345678", reason: "second")
        XCTAssertEqual(dataProvider.sets.count, 2)
        XCTAssertNotEqual(dataProvider.sets[0].id, dataProvider.sets[1].id)
    }

    func test_submitFoodItemReport_whenTheWriteFails_doesNotMarkMayHaveUnread() async throws {
        let store = UserNotificationsStoreFake()
        let (sut, dataProvider) = makeSUT(store: store)
        dataProvider.failingWriteCollections = [Constants.Firestore.foodItemReports]
        _ = try? await sut(barcode: "12345678", reason: "the fat is wrong")
        XCTAssertFalse(store.mayHaveUnread, "no ticket exists, so the bell has nothing to look for")
    }

    func test_submitFoodItemReport_afterASuccessfulWrite_marksThatTheUserMayHaveUnread() async throws {
        let store = UserNotificationsStoreFake()
        let (sut, _) = makeSUT(store: store)
        try await sut(barcode: "12345678", reason: "the fat is wrong")
        XCTAssertTrue(store.mayHaveUnread, "without it the gated bell would not query and the new notification would wait up to a day")
        XCTAssertNotNil(store.lastSubmittedAt, "a fetch already in flight must not overwrite the flag with a result that predates the submit")
    }

    func test_submitFoodItemReport_whenValidationFails_doesNotMarkMayHaveUnread() async throws {
        let store = UserNotificationsStoreFake()
        let (sut, _) = makeSUT(store: store)
        _ = try? await sut(barcode: "12345678", reason: "  ")
        XCTAssertFalse(store.mayHaveUnread)
    }

    func test_submitFoodItemReport_trimsWhitespaceFromReason() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(barcode: "12345678", reason: "  the fat is wrong  ")
        let json = try encodedJSON(try XCTUnwrap(dataProvider.sets.first?.item as? FoodItemReportCreateDTO))
        XCTAssertEqual(json["reason"] as? String, "the fat is wrong")
    }

    // MARK: - Helpers

    private func makeSUT(
        userId: String? = "test-user",
        store: UserNotificationsStoreFake = UserNotificationsStoreFake(),
        locale: String = "en"
    ) -> (sut: SubmitFoodItemReportUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = SubmitFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider, notificationsStore: store, locale: locale)
        return (sut, dataProvider)
    }

    private func encodedJSON(_ dto: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(dto)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
