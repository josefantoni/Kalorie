//
//  FetchUserNotificationsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class FetchUserNotificationsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetch_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetch_queriesOnlyTheUsersVisibleTickets_inBothCollections() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        _ = try await sut()
        let reports = TicketDataProviderFake.OrderedQuery(
            collection: Constants.Firestore.foodItemReports,
            field: "reported_by",
            value: "user-42",
            boolField: "hidden",
            boolValue: false,
            orderField: "reported_at",
            descending: true
        )
        let suggestions = TicketDataProviderFake.OrderedQuery(
            collection: Constants.Firestore.photoSuggestions,
            field: "suggested_by",
            value: "user-42",
            boolField: "hidden",
            boolValue: false,
            orderField: "suggested_at",
            descending: true
        )
        XCTAssertEqual(Set(dataProvider.orderedQueries), [reports, suggestions], "hidden tickets must never be downloaded — Firestore cannot filter on a comparison of two fields")
    }

    func test_fetch_mapsStateResponseAndDatesOfAResolvedReport() async throws {
        let (sut, dataProvider) = makeSUT()
        let resolvedAt = Date(timeIntervalSince1970: 1_700_100_000)
        dataProvider.stubbedReports = [
            makeReportDTO(id: "report-1", status: .accepted, response: "Fixed, thanks", resolvedAt: resolvedAt)
        ]
        let notifications = try await sut()
        let notification = try XCTUnwrap(notifications.first)
        XCTAssertEqual(notification.id, "report-1")
        XCTAssertEqual(notification.kind, .report)
        XCTAssertEqual(notification.status, .accepted)
        XCTAssertEqual(notification.response, "Fixed, thanks")
        XCTAssertEqual(notification.resolvedAt, resolvedAt)
        XCTAssertEqual(notification.reason, "wrong fat")
    }

    func test_fetch_aTicketResolvedAfterTheUserSawIt_isUnreadAgain() async throws {
        let (sut, dataProvider) = makeSUT()
        let created = Date(timeIntervalSince1970: 1_700_000_000)
        dataProvider.stubbedReports = [
            makeReportDTO(
                reportedAt: created,
                status: .accepted,
                response: "Done",
                resolvedAt: created.addingTimeInterval(3_600),
                seenAt: created.addingTimeInterval(60)
            )
        ]
        let notifications = try await sut()
        let notification = try XCTUnwrap(notifications.first)
        XCTAssertTrue(notification.isUnread, "the resolution is a new event, so a read marker from before it no longer counts")
    }

    func test_fetch_aTicketSeenAfterItsLastEvent_isRead() async throws {
        let (sut, dataProvider) = makeSUT()
        let created = Date(timeIntervalSince1970: 1_700_000_000)
        dataProvider.stubbedReports = [makeReportDTO(reportedAt: created, seenAt: created.addingTimeInterval(60))]
        let notifications = try await sut()
        let notification = try XCTUnwrap(notifications.first)
        XCTAssertFalse(notification.isUnread)
    }

    func test_fetch_ordersBothKindsByTheirLastEvent_newestFirst() async throws {
        let (sut, dataProvider) = makeSUT()
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        dataProvider.stubbedReports = [
            makeReportDTO(id: "old-report", reportedAt: base),
            makeReportDTO(id: "resolved-report", reportedAt: base.addingTimeInterval(10), status: .accepted, response: "ok", resolvedAt: base.addingTimeInterval(500))
        ]
        dataProvider.stubbedSuggestions = [makeSuggestionDTO(id: "photo", suggestedAt: base.addingTimeInterval(100))]
        let ids = try await sut().map(\.id)
        XCTAssertEqual(ids, ["resolved-report", "photo", "old-report"])
    }

    func test_fetch_aResolvedPhoto_hasNoPreviewBecauseItsFileIsDeleted() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = [
            makeSuggestionDTO(id: "pending-photo"),
            makeSuggestionDTO(id: "approved-photo", status: .accepted, response: "ok", resolvedAt: Date(timeIntervalSince1970: 1_700_100_000))
        ]
        let fetched = try await sut()
        let byId = Dictionary(uniqueKeysWithValues: fetched.map { ($0.id, $0) })
        XCTAssertNotNil(byId["pending-photo"]?.photoURL)
        XCTAssertNil(byId["approved-photo"]?.photoURL)
    }

    func test_fetch_whenEitherQueryFails_throws() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.failingLoadCollections = [Constants.Firestore.photoSuggestions]
        do {
            _ = try await sut()
            XCTFail("a half list would show a bell without the missing tickets")
        } catch {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchUserNotificationsUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = FetchUserNotificationsUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
