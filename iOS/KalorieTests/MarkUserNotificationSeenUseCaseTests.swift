//
//  MarkUserNotificationSeenUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class MarkUserNotificationSeenUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_markSeen_whenUnread_writesOneSeenAtToTheTicketDocument() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(makeNotification(kind: .photoSuggestion, id: "photo-7"))
        XCTAssertEqual(dataProvider.updates.count, 1)
        XCTAssertEqual(dataProvider.updates.first?.id, "photo-7")
        XCTAssertEqual(dataProvider.updates.first?.collection, Constants.Firestore.photoSuggestions)
        XCTAssertTrue(dataProvider.updates.first?.item is NotificationSeenDTO)
    }

    func test_markSeen_whenAlreadyRead_writesNothing() async throws {
        let (sut, dataProvider) = makeSUT()
        let created = Date(timeIntervalSince1970: 1_700_000_000)
        try await sut(makeNotification(createdAt: created, seenAt: created.addingTimeInterval(10)))
        XCTAssertTrue(dataProvider.updates.isEmpty, "opening the detail of a read notification must cost nothing")
    }

    func test_markSeen_whenSeenBeforeTheResolution_writesAgain() async throws {
        let (sut, dataProvider) = makeSUT()
        let created = Date(timeIntervalSince1970: 1_700_000_000)
        try await sut(makeNotification(
            createdAt: created,
            status: .accepted,
            resolvedAt: created.addingTimeInterval(3_600),
            seenAt: created.addingTimeInterval(10)
        ))
        XCTAssertEqual(dataProvider.updates.count, 1, "the resolution is a new event and the rules allow repeated seen_at writes")
    }

    func test_markSeen_whenTheWriteFails_throwsSoTheRowStaysUnread() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.failingWriteCollections = [Constants.Firestore.foodItemReports]
        do {
            try await sut(makeNotification())
            XCTFail("a swallowed failure would show the row as read although nothing was stored")
        } catch {}
    }

    func test_markSeen_whenNotAuthenticated_throws() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeNotification())
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: MarkUserNotificationSeenUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = MarkUserNotificationSeenUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
