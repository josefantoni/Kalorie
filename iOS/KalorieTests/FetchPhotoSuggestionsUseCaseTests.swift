//
//  FetchPhotoSuggestionsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class FetchPhotoSuggestionsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_fetch_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            _ = try await sut()
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
    }

    func test_fetch_loadsOnlyPendingSuggestions_newestFirst() async throws {
        let (sut, dataProvider) = makeSUT()
        _ = try await sut()
        let expected = TicketDataProviderFake.OrderedQuery(
            collection: Constants.Firestore.photoSuggestions,
            field: "status",
            value: "pending",
            boolField: nil,
            boolValue: nil,
            orderField: "suggested_at",
            descending: true
        )
        XCTAssertEqual(dataProvider.orderedQueries, [expected])
    }

    func test_fetch_returnsEveryPendingSuggestionForAnItem() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = [
            makeSuggestionDTO(id: "a", itemId: "12345678"),
            makeSuggestionDTO(id: "b", itemId: "12345678")
        ]
        let suggestions = try await sut()
        XCTAssertEqual(suggestions.map(\.id), ["a", "b"], "approving one must leave the others pending")
    }

    func test_fetch_skipsADocumentWhoseUrlIsInvalid() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSuggestions = [
            PhotoSuggestionDTO(
                id: "bad",
                itemId: "12345678",
                suggestedBy: "u",
                photoUrl: "",
                suggestedAt: 1,
                status: .pending,
                locale: "en",
                response: nil,
                resolvedAt: nil,
                seenAt: nil
            ),
            makeSuggestionDTO(id: "good")
        ]
        let suggestions = try await sut()
        XCTAssertEqual(suggestions.map(\.id), ["good"])
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer") -> (sut: FetchPhotoSuggestionsUseCase, dataProvider: TicketDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let sut = FetchPhotoSuggestionsUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}
