//
//  HideUserNotificationUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class HideUserNotificationUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_hide_writesHiddenTrueToTheTicketAndDeletesNothing() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeNotification(id: "report-3"))
        XCTAssertEqual(dataProvider.updates.map(\.id), ["report-3"])
        XCTAssertEqual(dataProvider.updates.first?.collection, Constants.Firestore.foodItemReports)
        let json = try JSONSerialization.jsonObject(
            with: JSONEncoder().encode(try XCTUnwrap(dataProvider.updates.first?.item as? NotificationHiddenDTO))
        ) as? [String: Any]
        XCTAssertEqual(json as? [String: Bool], ["hidden": true], "the rules allow the author to change only seen_at and hidden, and hidden only to true")
        XCTAssertTrue(dataProvider.deletes.isEmpty, "hiding is local to the user's list — the maintainer's ticket stays")
    }

    func test_hide_whenTheWriteFails_throws() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.failingWriteCollections = [Constants.Firestore.photoSuggestions]
        do {
            try await sut(makeNotification(kind: .photoSuggestion))
            XCTFail("the caller must restore the row")
        } catch {}
    }

    func test_hide_whenNotAuthenticated_throws() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeNotification())
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: HideUserNotificationUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = HideUserNotificationUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
