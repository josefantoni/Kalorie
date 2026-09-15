//
//  ModerationReviewViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.09.2026.
//

import XCTest
@testable import Kalorie

final class ModerationReviewViewModelTests: XCTestCase {

    // MARK: - onApproveTapped

    @MainActor
    func test_onApproveTapped_preservesTheOriginalSubmissionsDate() async {
        let originalDate = Date(timeIntervalSince1970: 1_700_000_000)
        let submission = makeSubmission(date: originalDate)
        let approveSubmission = ApproveSubmissionUseCaseSpy()
        let sut = makeSUT(submission: submission, approveSubmission: approveSubmission)

        sut.formInput.name = "Opravený název"
        await sut.onApproveTapped()

        XCTAssertEqual(approveSubmission.receivedItem?.date, originalDate, "approving must not silently reset the submission's original date")
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onApproveTapped_preservesTheOriginalEnglishName() async {
        let submission = makeSubmission(engName: "Cottage cheese")
        let approveSubmission = ApproveSubmissionUseCaseSpy()
        let sut = makeSUT(submission: submission, approveSubmission: approveSubmission)

        sut.formInput.name = "Opravený název"
        await sut.onApproveTapped()

        XCTAssertEqual(approveSubmission.receivedItem?.engName, "Cottage cheese", "editing an unrelated field must not blank the English name")
    }

    // MARK: - Helpers

    private func makeSUT(
        submission: FoodItemSubmissionDomain,
        approveSubmission: any ApproveSubmissionUseCaseProtocol = ApproveSubmissionUseCaseFake(),
        rejectSubmission: any RejectSubmissionUseCaseProtocol = RejectSubmissionUseCaseFake()
    ) -> ModerationReviewViewModel {
        ModerationReviewViewModel(
            submission: submission,
            approveSubmission: approveSubmission,
            rejectSubmission: rejectSubmission
        ) {}
    }

    private func makeSubmission(
        id: String = "sub-1",
        barcode: String = "12345678",
        date: Date = .now,
        engName: String = "Cottage cheese"
    ) -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: barcode,
            submittedBy: "some-user",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: FoodItemDomain(
                id: barcode,
                kind: .catalogue,
                czName: "Tvaroh",
                engName: engName,
                weight: 200,
                date: date,
                energyKJ: 335,
                caloriesPerHundredGrams: 80,
                fat: 0.5,
                fatSaturated: 0.3,
                fatUnsaturatedFattyAcids: 0.2,
                carbohydrate: 4,
                carbohydratePureSugar: 3,
                fiber: 0,
                protein: 13,
                salt: 0.1
            )
        )
    }
}

private final class ApproveSubmissionUseCaseSpy: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    private(set) var receivedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain) async throws {
        receivedItem = item
    }
}
