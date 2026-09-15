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

    // MARK: - onApproveTapped error handling

    @MainActor
    func test_onApproveTapped_whenValidationFails_showsFieldSpecificMessageAndDoesNotDismiss() async {
        let approveSubmission = ApproveSubmissionUseCaseSpy()
        approveSubmission.errorToThrow = CreateFoodItemError.invalidWeight
        let sut = makeSUT(submission: makeSubmission(), approveSubmission: approveSubmission)

        await sut.onApproveTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorInvalidWeight)
        XCTAssertFalse(sut.shouldDismiss)
    }

    @MainActor
    func test_onApproveTapped_whenBarcodeCollides_showsAlreadyExistsMessage() async {
        let approveSubmission = ApproveSubmissionUseCaseSpy()
        approveSubmission.errorToThrow = CreateFoodItemError.itemAlreadyExists
        let sut = makeSUT(submission: makeSubmission(), approveSubmission: approveSubmission)

        await sut.onApproveTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorAlreadyExists)
        XCTAssertFalse(sut.shouldDismiss)
    }

    @MainActor
    func test_onApproveTapped_whenSubmissionAlreadyResolved_showsMessageAndDismisses() async {
        let approveSubmission = ApproveSubmissionUseCaseSpy()
        approveSubmission.errorToThrow = ApproveSubmissionError.alreadyResolved
        let sut = makeSUT(submission: makeSubmission(), approveSubmission: approveSubmission)

        await sut.onApproveTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorAlreadyResolved)
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onApproveTapped_whenSubmissionChangedSinceReview_showsMessageAndDismisses() async {
        let approveSubmission = ApproveSubmissionUseCaseSpy()
        approveSubmission.errorToThrow = ApproveSubmissionError.changedSinceReview
        let sut = makeSUT(submission: makeSubmission(), approveSubmission: approveSubmission)

        await sut.onApproveTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorChangedSinceReview)
        XCTAssertTrue(sut.shouldDismiss)
    }

    // MARK: - onRejectConfirmed error handling

    @MainActor
    func test_onRejectConfirmed_whenSubmissionChangedSinceReview_showsMessageAndDismisses() async {
        let rejectSubmission = RejectSubmissionUseCaseFake(errorToThrow: RejectSubmissionError.changedSinceReview)
        let sut = makeSUT(submission: makeSubmission(), rejectSubmission: rejectSubmission)
        sut.rejectReason = "Wrong calories"

        await sut.onRejectConfirmed()

        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorChangedSinceReview)
        XCTAssertTrue(sut.shouldDismiss)
    }

    // MARK: - onNutritionLabelCaptured

    @MainActor
    func test_onNutritionLabelCaptured_onSuccess_closesCameraAndMergesWithoutTouchingFilledFields() async {
        let submission = makeSubmission()
        let sut = makeSUT(
            submission: submission,
            recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(stubbedReading: NutritionLabelReading(fat: 999, fiber: 1))
        )
        sut.isNutritionLabelCameraVisible = true
        let originalFat = sut.formInput.fat

        await sut.onNutritionLabelCaptured(UIImage(), liveBarcode: nil)

        XCTAssertFalse(sut.isNutritionLabelCameraVisible, "a successful capture on an already-open form must close the camera without a push")
        XCTAssertEqual(sut.formInput.fat, originalFat, "a field already holding a submitted value must never be overwritten by the photo")
        XCTAssertEqual(sut.formInput.fiber, 1, "a field still at its default must be filled")
    }

    // MARK: - Helpers

    private func makeSUT(
        submission: FoodItemSubmissionDomain,
        approveSubmission: any ApproveSubmissionUseCaseProtocol = ApproveSubmissionUseCaseFake(),
        rejectSubmission: any RejectSubmissionUseCaseProtocol = RejectSubmissionUseCaseFake(),
        recognizeNutritionLabel: any RecognizeNutritionLabelUseCaseProtocol = RecognizeNutritionLabelUseCaseFake(),
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProviderFake()
    ) -> ModerationReviewViewModel {
        ModerationReviewViewModel(
            submission: submission,
            approveSubmission: approveSubmission,
            rejectSubmission: rejectSubmission,
            recognizeNutritionLabel: recognizeNutritionLabel,
            cameraAuthorizationProvider: cameraAuthorizationProvider
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
    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain) async throws {
        receivedItem = item
        if let errorToThrow { throw errorToThrow }
    }
}
