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

    func test_fetchMyFoodItemReport_asksOnlyForTheUsersPendingTicketForThatItem() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        _ = try await sut(barcode: "12345678")
        let query = try XCTUnwrap(dataProvider.equalityQueries.first)
        XCTAssertEqual(query.collection, Constants.Firestore.foodItemReports)
        XCTAssertEqual(
            Set(query.filters),
            ["reported_by==user-42", "barcode==12345678", "status==pending"],
            "a resolved ticket must not block a new report for the same item"
        )
        XCTAssertEqual(query.limit, 1)
    }

    func test_fetchMyFoodItemReport_whenNoPendingTicket_returnsNil() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(barcode: "12345678")
        XCTAssertNil(result)
    }

    func test_fetchMyFoodItemReport_whenAPendingTicketExists_returnsIt() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        dataProvider.stubbedReports = [makeReportDTO(id: "report-9", reportedBy: "user-42")]
        let result = try await sut(barcode: "12345678")
        XCTAssertEqual(result?.id, "report-9")
        XCTAssertEqual(result?.reason, "wrong fat")
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchMyFoodItemReportUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = FetchMyFoodItemReportUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
