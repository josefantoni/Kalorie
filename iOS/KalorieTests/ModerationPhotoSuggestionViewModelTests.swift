//
//  ModerationPhotoSuggestionViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class ModerationPhotoSuggestionViewModelTests: XCTestCase {

    // MARK: - Tests

    @MainActor
    func test_onApproveTapped_reportsResolvedAsApproved() async {
        var reported: [Bool] = []
        let sut = makeSUT { reported.append($0) }

        await sut.onApproveTapped()

        XCTAssertEqual(reported, [true], "the list drops sibling suggestions only after an approval")
    }

    @MainActor
    func test_onRejectTapped_reportsResolvedAsNotApproved() async {
        var reported: [Bool] = []
        let sut = makeSUT { reported.append($0) }

        await sut.onRejectTapped()

        XCTAssertEqual(reported, [false], "a rejection must not drop other users' suggestions")
    }

    // MARK: - Helpers

    @MainActor
    private func makeSUT(onResolved: @escaping (Bool) -> Void) -> ModerationPhotoSuggestionViewModel {
        ModerationPhotoSuggestionViewModel(
            suggestion: PhotoSuggestionDomain(
                itemId: "111",
                suggestedBy: "alice",
                photoURL: URL(fileURLWithPath: "/photo.jpg"),
                suggestedAt: .now
            ),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            approvePhotoSuggestion: ApprovePhotoSuggestionUseCaseFake(),
            rejectPhotoSuggestion: RejectPhotoSuggestionUseCaseFake(),
            onResolved: onResolved
        )
    }
}
