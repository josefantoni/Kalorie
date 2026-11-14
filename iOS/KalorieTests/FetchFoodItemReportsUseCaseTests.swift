//
//  FetchFoodItemReportsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class FetchFoodItemReportsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetch_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetch_loadsOnlyPendingReports_newestFirst() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        let expected = TicketDataProviderFake.OrderedQuery(
            collection: Constants.Firestore.foodItemReports,
            field: "status",
            value: "pending",
            boolField: nil,
            boolValue: nil,
            orderField: "reported_at",
            descending: true
        )
        XCTAssertEqual(dataProvider.orderedQueries, [expected], "resolved tickets are kept for their authors and must not clutter the maintainer's queue")
    }

    func test_fetch_returnsOneEntryPerTicket_evenForTheSameItem() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedReports = [
            makeReportDTO(id: "a", barcode: "12345678"),
            makeReportDTO(id: "b", barcode: "12345678")
        ]
        let reports = try await sut()
        XCTAssertEqual(reports.map(\.id), ["a", "b"], "two reports about different fields are answered separately")
    }

    func test_fetch_keepsTheOwnerlessTicketOfADeletedAccount() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedReports = [makeReportDTO(id: "orphan", reportedBy: nil)]
        let reports = try await sut()
        XCTAssertEqual(reports.map(\.id), ["orphan"])
        XCTAssertNil(reports.first?.reportedBy)
    }

    func test_fetch_whenTheQueryFails_throws() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.failingLoadCollections = [Constants.Firestore.foodItemReports]
        do {
            _ = try await sut()
            XCTFail("Expected the query error")
        } catch {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer") -> (sut: FetchFoodItemReportsUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = FetchFoodItemReportsUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
