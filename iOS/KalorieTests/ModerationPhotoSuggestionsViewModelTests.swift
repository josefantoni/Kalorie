//
//  ModerationPhotoSuggestionsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class ModerationPhotoSuggestionsViewModelTests: XCTestCase {

    // MARK: - Tests

    @MainActor
    func test_onAppear_listsTheOldestSuggestionFirst_whateverOrderTheFetchReturns() async throws {
        let url = try XCTUnwrap(URL(string: "https://example.com/p.jpg"))
        let newest = PhotoSuggestionDomain(itemId: "33333333", suggestedBy: "alice", photoURL: url, suggestedAt: Date(timeIntervalSince1970: 300))
        let oldest = PhotoSuggestionDomain(itemId: "11111111", suggestedBy: "alice", photoURL: url, suggestedAt: Date(timeIntervalSince1970: 100))
        let middle = PhotoSuggestionDomain(itemId: "22222222", suggestedBy: "alice", photoURL: url, suggestedAt: Date(timeIntervalSince1970: 200))
        let sut = ModerationPhotoSuggestionsViewModel(
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [newest, oldest, middle]),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
        )

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.suggestion.itemId), ["11111111", "22222222", "33333333"], "the longest-waiting suggestion is reviewed first")
    }

    @MainActor
    func test_onSuggestionResolved_whenApproved_dropsEverySuggestionForTheSameItem() async throws {
        let sut = try await makeLoadedSUT()

        sut.onSuggestionResolved(id: "111_alice", isApproved: true)

        XCTAssertEqual(sut.rows.map(\.id), ["222_alice"], "the approval removed every user's suggestion for the item, so the sibling row is stale")
    }

    @MainActor
    func test_onSuggestionResolved_whenRejected_dropsOnlyThatSuggestion() async throws {
        let sut = try await makeLoadedSUT()

        sut.onSuggestionResolved(id: "111_alice", isApproved: false)

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["111_bob", "222_alice"], "a rejection leaves other users' suggestions for the item waiting")
    }

    @MainActor
    func test_onAppear_afterReturningFromADetail_keepsTheRowsResolvedThere() async throws {
        let sut = try await makeLoadedSUT()
        sut.onSuggestionResolved(id: "111_alice", isApproved: false)

        await sut.onAppear()

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["111_bob", "222_alice"], "popping a pushed detail re-runs .task; a re-fetch would bring back a row the maintainer just resolved")
    }

    // MARK: - Helpers

    @MainActor
    private func makeLoadedSUT() async throws -> ModerationPhotoSuggestionsViewModel {
        let url = try XCTUnwrap(URL(string: "https://example.com/p.jpg"))
        let sut = ModerationPhotoSuggestionsViewModel(
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [
                PhotoSuggestionDomain(itemId: "111", suggestedBy: "alice", photoURL: url, suggestedAt: Date(timeIntervalSince1970: 100)),
                PhotoSuggestionDomain(itemId: "111", suggestedBy: "bob", photoURL: url, suggestedAt: Date(timeIntervalSince1970: 200)),
                PhotoSuggestionDomain(itemId: "222", suggestedBy: "alice", photoURL: url, suggestedAt: Date(timeIntervalSince1970: 300))
            ]),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
        )
        await sut.onAppear()
        return sut
    }
}
