//
//  ResolveFoodItemReportUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class ResolveFoodItemReportUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_resolve_writesStatusResponseResolvedAtAndHiddenFalse_andDeletesNothing() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(ticketId: "report-3", status: .accepted, response: "  Fixed, thanks  ")
        XCTAssertEqual(dataProvider.updates.map(\.id), ["report-3"])
        XCTAssertEqual(dataProvider.updates.first?.collection, Constants.Firestore.foodItemReports)
        let json = try encodedJSON(try XCTUnwrap(dataProvider.updates.first?.item as? TicketResolutionDTO))
        XCTAssertEqual(Set(json.keys), ["status", "response", "resolved_at", "hidden"], "the resolve rule allows exactly these keys")
        XCTAssertEqual(json["status"] as? String, "accepted")
        XCTAssertEqual(json["response"] as? String, "Fixed, thanks")
        XCTAssertEqual(json["hidden"] as? Bool, false, "a ticket the author hid while pending must come back")
        XCTAssertTrue(dataProvider.deletes.isEmpty, "the ticket is the record of the resolution and is kept")
    }

    func test_resolve_withRejected_writesRejected() async throws {
        let (sut, dataProvider) = makeSUT()
        try await sut(ticketId: "report-3", status: .rejected, response: "Not reproducible")
        let json = try encodedJSON(try XCTUnwrap(dataProvider.updates.first?.item as? TicketResolutionDTO))
        XCTAssertEqual(json["status"] as? String, "rejected")
    }

    func test_resolve_withEmptyOrWhitespaceResponse_throwsWithoutWriting() async throws {
        let (sut, dataProvider) = makeSUT()
        for response in ["", "   \n "] {
            do {
                try await sut(ticketId: "report-3", status: .accepted, response: response)
                XCTFail("Expected responseRequired error")
            } catch TicketResolutionError.responseRequired {}
        }
        XCTAssertTrue(dataProvider.updates.isEmpty, "the response is the only thing the author learns from a resolution")
    }

    func test_resolve_withResponseOverTheLimitInUTF16Units_throwsWithoutWriting() async throws {
        let (sut, dataProvider) = makeSUT()
        let tooLong = String(repeating: "😀", count: Constants.Firestore.responseMaxLength)
        do {
            try await sut(ticketId: "report-3", status: .accepted, response: tooLong)
            XCTFail("Expected responseTooLong error")
        } catch TicketResolutionError.responseTooLong {}
        XCTAssertTrue(dataProvider.updates.isEmpty, "the rules count UTF-16 units")
    }

    func test_resolve_whenNotAuthenticated_throws() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(ticketId: "report-3", status: .accepted, response: "ok")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_resolve_whenTheWriteFails_throws() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.failingWriteCollections = [Constants.Firestore.foodItemReports]
        do {
            try await sut(ticketId: "report-3", status: .accepted, response: "ok")
            XCTFail("the editor must keep the sheet open for a retry")
        } catch {}
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer") -> (sut: ResolveFoodItemReportUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = ResolveFoodItemReportUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }

    private func encodedJSON(_ dto: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(dto)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
