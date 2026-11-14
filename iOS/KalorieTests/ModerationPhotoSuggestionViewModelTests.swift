//
//  ModerationPhotoSuggestionViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import XCTest
@testable import Kalorie

final class ModerationPhotoSuggestionViewModelTests: XCTestCase {

    // MARK: - Tests

    @MainActor
    func test_onApproveTapped_opensTheSheetPrefilledInTheTicketsLocale_notTheMaintainersOwn() {
        let (czech, _, _) = makeSUT(locale: "cs")
        let (english, _, _) = makeSUT(locale: "en")

        czech.onApproveTapped()
        english.onApproveTapped()

        XCTAssertEqual(czech.responseDraft?.status, .accepted)
        XCTAssertEqual(czech.responseDraft?.text, "Díky za fotku! Přidal jsem ji k potravině.")
        XCTAssertEqual(english.responseDraft?.text, "Thanks for the photo! I have added it to the food.")
    }

    @MainActor
    func test_onRejectTapped_opensAnEmptySheet_thatCannotBeSent() {
        let (sut, approve, reject) = makeSUT()

        sut.onRejectTapped()

        XCTAssertEqual(sut.responseDraft?.status, .rejected)
        XCTAssertEqual(sut.responseDraft?.text, "")
        XCTAssertEqual(sut.responseDraft?.isSendable, false, "a rejection must be explained to the author")
        XCTAssertTrue(approve.calls.isEmpty && reject.calls.isEmpty, "tapping Reject alone changes nothing")
    }

    @MainActor
    func test_onResponseSendTapped_whenApproving_approvesWithTheEditedResponseAndResolves() async {
        var resolved = 0
        let (sut, approve, reject) = makeSUT { resolved += 1 }
        sut.onApproveTapped()
        sut.responseDraft?.text = "Great photo"

        await sut.onResponseSendTapped()

        XCTAssertEqual(approve.calls, ["Great photo"])
        XCTAssertTrue(reject.calls.isEmpty)
        XCTAssertEqual(resolved, 1)
        XCTAssertNil(sut.responseDraft)
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onResponseSendTapped_whenRejecting_rejectsWithTheResponse() async {
        let (sut, approve, reject) = makeSUT()
        sut.onRejectTapped()
        sut.responseDraft?.text = "Out of focus"

        await sut.onResponseSendTapped()

        XCTAssertEqual(reject.calls, ["Out of focus"])
        XCTAssertTrue(approve.calls.isEmpty)
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onResponseSendTapped_withEmptyText_doesNothing() async {
        let (sut, _, reject) = makeSUT()
        sut.onRejectTapped()

        await sut.onResponseSendTapped()

        XCTAssertTrue(reject.calls.isEmpty)
        XCTAssertNotNil(sut.responseDraft)
    }

    @MainActor
    func test_onResponseSendTapped_whenTheWriteFails_keepsTheSheetOpenForARetry() async {
        var resolved = 0
        let (sut, approve, _) = makeSUT { resolved += 1 }
        approve.error = URLError(.notConnectedToInternet)
        sut.onApproveTapped()

        await sut.onResponseSendTapped()

        XCTAssertNotNil(sut.responseDraft, "the maintainer must not have to retype the response")
        XCTAssertNotNil(sut.responseDraft?.errorTitle)
        XCTAssertEqual(resolved, 0)
        XCTAssertFalse(sut.shouldDismiss)
    }

    @MainActor
    func test_onResponseSendTapped_whenTheItemIsGone_closesTheSheetAndBlocksApproval() async {
        let (sut, approve, _) = makeSUT()
        approve.error = ApprovePhotoSuggestionError.itemMissing
        sut.onApproveTapped()

        await sut.onResponseSendTapped()

        XCTAssertNil(sut.responseDraft)
        XCTAssertFalse(sut.canApprove)
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onApproveTapped_whenAnItemWithAPhotoAlreadyExists_stillOffersApproval() async {
        let photoURL = URL(string: "https://example.com/p.jpg")
        let item = makePhotoSuggestionItem(photoURL: photoURL)
        let (sut, _, _) = makeSUT(item: FoodItemDTO(item: item).asDomain())

        await sut.onAppear()

        XCTAssertTrue(sut.canApprove, "approving replaces the current catalogue photo")
    }

    // MARK: - Helpers

    @MainActor
    private func makeSUT(
        locale: String = "en",
        item: FoodItemDomain? = makePhotoSuggestionItem(),
        onResolved: @escaping () -> Void = {}
    ) -> (sut: ModerationPhotoSuggestionViewModel, approve: ApproveRecorder, reject: RejectRecorder) {
        let approve = ApproveRecorder()
        let reject = RejectRecorder()
        let sut = ModerationPhotoSuggestionViewModel(
            suggestion: makeSuggestion(itemId: "111", locale: locale),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            approvePhotoSuggestion: approve,
            rejectPhotoSuggestion: reject,
            onResolved: onResolved
        )
        return (sut, approve, reject)
    }
}

final class ApproveRecorder: ApprovePhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var error: Error?
    private(set) var calls: [String] = []

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws {
        if let error { throw error }
        calls.append(response)
    }
}

final class RejectRecorder: RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    var error: Error?
    private(set) var calls: [String] = []

    // MARK: - Functions

    func callAsFunction(_ suggestion: PhotoSuggestionDomain, response: String) async throws {
        if let error { throw error }
        calls.append(response)
    }
}
