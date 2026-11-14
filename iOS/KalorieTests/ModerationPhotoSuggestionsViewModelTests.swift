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
    func test_onAppear_listsOneRowPerTicketInTheOrderTheQueueReturnsThem() async throws {
        let sut = try await makeLoadedSUT()

        XCTAssertEqual(sut.rows.map(\.id), ["a", "b", "c"], "the use case orders the queue newest first; the list must not reorder it")
    }

    @MainActor
    func test_onSuggestionResolved_dropsOnlyThatTicket_evenWhenAnotherTargetsTheSameItem() async throws {
        let sut = try await makeLoadedSUT()

        sut.onSuggestionResolved(id: "a")

        XCTAssertEqual(sut.rows.map(\.id), ["b", "c"], "approving one suggestion leaves the other pending ones for the item waiting")
    }

    @MainActor
    func test_onAppear_afterReturningFromADetail_keepsTheRowsResolvedThere() async throws {
        let sut = try await makeLoadedSUT()
        sut.onSuggestionResolved(id: "a")

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["b", "c"], "popping a pushed detail re-runs .task; a re-fetch would bring back a row the maintainer just resolved")
    }

    // MARK: - Helpers

    @MainActor
    private func makeLoadedSUT() async throws -> ModerationPhotoSuggestionsViewModel {
        let sut = ModerationPhotoSuggestionsViewModel(
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [
                makeSuggestion(id: "a", itemId: "111"),
                makeSuggestion(id: "b", itemId: "111"),
                makeSuggestion(id: "c", itemId: "222")
            ]),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
        )
        await sut.onAppear()
        return sut
    }
}
