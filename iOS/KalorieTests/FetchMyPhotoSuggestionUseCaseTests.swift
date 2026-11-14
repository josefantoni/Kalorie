//
//  FetchMyPhotoSuggestionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class FetchMyPhotoSuggestionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetchMyPhotoSuggestion_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(itemId: "12345678")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetchMyPhotoSuggestion_asksOnlyForTheUsersPendingTicketForThatItem() async throws {
        let (sut, dataProvider) = makeSUT(userId: "user-42")
        _ = try await sut(itemId: "12345678")
        let query = try XCTUnwrap(dataProvider.equalityQueries.first)
        XCTAssertEqual(query.collection, Constants.Firestore.photoSuggestions)
        XCTAssertEqual(
            Set(query.filters),
            ["suggested_by==user-42", "item_id==12345678", "status==pending"],
            "a resolved suggestion must not block a new one for the same item"
        )
        XCTAssertEqual(query.limit, 1)
    }

    func test_fetchMyPhotoSuggestion_withoutAPendingTicket_returnsFalse() async throws {
        let (sut, _) = makeSUT()
        let result = try await sut(itemId: "12345678")
        XCTAssertFalse(result)
    }

    func test_fetchMyPhotoSuggestion_withAPendingTicket_returnsTrue() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = [makeSuggestionDTO()]
        let result = try await sut(itemId: "12345678")
        XCTAssertTrue(result)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: FetchMyPhotoSuggestionUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = FetchMyPhotoSuggestionUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
