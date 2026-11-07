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
}
