//
//  ModerationQueueViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 11.09.2026.
//

import XCTest
@testable import Kalorie

final class ModerationQueueViewModelTests: XCTestCase {

    // MARK: - onAppear

    @MainActor
    func test_onAppear_marksSubmissionsWithCollidingBarcodesAsColliding() async {
        let submissionA = makeSubmission(id: "a", barcode: "111")
        let submissionB = makeSubmission(id: "b", barcode: "222")
        let sut = makeSUT(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(stubbedSubmissions: [submissionA, submissionB]),
            fetchFoodItemByBarcode: BarcodeLookupStub(existingBarcodes: ["111"])
        )
        await sut.onAppear()
        XCTAssertTrue(sut.isColliding(submissionA))
        XCTAssertFalse(sut.isColliding(submissionB))
    }

    // MARK: - onSubmissionResolved

    @MainActor
    func test_onSubmissionResolved_removesOnlyThatSubmissionWithoutRefetchingTheQueue() async {
        let submissionA = makeSubmission(id: "a", barcode: "111")
        let submissionB = makeSubmission(id: "b", barcode: "222")
        let fetchPendingSubmissions = FetchPendingSubmissionsUseCaseSpy(stubbedSubmissions: [submissionA, submissionB])
        let sut = makeSUT(fetchPendingSubmissions: fetchPendingSubmissions, fetchFoodItemByBarcode: BarcodeLookupStub(existingBarcodes: []))
        await sut.onAppear()
        XCTAssertEqual(fetchPendingSubmissions.callCount, 1)

        await sut.onSubmissionResolved(id: "a")

        XCTAssertEqual(sut.submissions.map(\.id), ["b"])
        XCTAssertEqual(fetchPendingSubmissions.callCount, 1, "resolving one submission must not re-fetch the whole queue")
    }

    @MainActor
    func test_onSubmissionResolved_recomputesCollisionsForRemainingSubmissions() async {
        let submissionA = makeSubmission(id: "a", barcode: "111")
        let submissionB = makeSubmission(id: "b", barcode: "222")
        let barcodeLookup = BarcodeLookupStub(existingBarcodes: [])
        let sut = makeSUT(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(stubbedSubmissions: [submissionA, submissionB]),
            fetchFoodItemByBarcode: barcodeLookup
        )
        await sut.onAppear()
        XCTAssertFalse(sut.isColliding(submissionB))

        barcodeLookup.existingBarcodes.insert("222")
        await sut.onSubmissionResolved(id: "a")

        XCTAssertTrue(sut.isColliding(submissionB), "approving one submission can make another pending submission's barcode collide")
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol = FetchPendingSubmissionsUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake()
    ) -> ModerationQueueViewModel {
        ModerationQueueViewModel(fetchPendingSubmissions: fetchPendingSubmissions, fetchFoodItemByBarcode: fetchFoodItemByBarcode)
    }

    private func makeSubmission(id: String, barcode: String) -> FoodItemSubmissionDomain {
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
                engName: "Cottage cheese",
                weight: 200,
                date: .now,
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

private final class FetchPendingSubmissionsUseCaseSpy: FetchPendingSubmissionsUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0
    var stubbedSubmissions: [FoodItemSubmissionDomain]

    // MARK: - Init

    init(stubbedSubmissions: [FoodItemSubmissionDomain]) {
        self.stubbedSubmissions = stubbedSubmissions
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [FoodItemSubmissionDomain] {
        callCount += 1
        return stubbedSubmissions
    }
}

private final class BarcodeLookupStub: FetchFoodItemByBarcodeUseCaseProtocol {

    // MARK: - Properties

    var existingBarcodes: Set<String>

    // MARK: - Init

    init(existingBarcodes: Set<String>) {
        self.existingBarcodes = existingBarcodes
    }

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? {
        guard existingBarcodes.contains(barcode) else { return nil }
        return FoodItemDomain(
            id: barcode,
            kind: .catalogue,
            czName: "Existing",
            engName: "Existing",
            weight: 100,
            date: .now,
            energyKJ: 100,
            caloriesPerHundredGrams: 50,
            fat: 1,
            fatSaturated: 0,
            fatUnsaturatedFattyAcids: 1,
            carbohydrate: 1,
            carbohydratePureSugar: 1,
            fiber: 0,
            protein: 1,
            salt: 0.1
        )
    }
}
