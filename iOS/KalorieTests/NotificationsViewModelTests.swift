//
//  NotificationsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class NotificationsViewModelTests: XCTestCase {

    // MARK: - Tests

    @MainActor
    func test_onAppear_whenEverySourceSucceeds_showsAllRowsAndNoAlert() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [makeReport(barcode: "111")]),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [makeSuggestion(itemId: "222")])
        )

        await sut.onAppear()

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["report-111", "photo-222_alice"])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenPhotoSuggestionsFail_stillShowsReportsAndAlerts() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [makeReport(barcode: "111")]),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(shouldThrow: true)
        )

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["report-111"], "one failing query (e.g. rules or index not deployed yet) must not hide what the other sources loaded")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenReportsFail_stillShowsPhotoSuggestionsAndAlerts() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(shouldThrow: true),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [makeSuggestion(itemId: "222")])
        )

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["photo-222_alice"])
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenEverySourceFails_showsNoRowsAndAlerts() async {
        let sut = makeSUT(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(shouldThrow: true),
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(shouldThrow: true),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(shouldThrow: true)
        )

        await sut.onAppear()

        XCTAssertTrue(sut.rows.isEmpty)
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onRefresh_whenPhotoSuggestionsFailAfterALoad_keepsTheRowsAlreadyShownAndAlerts() async {
        let suggestions = SwitchableFetchPhotoSuggestions(stubbedSuggestions: [makeSuggestion(itemId: "222")])
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [makeReport(barcode: "111")]),
            fetchPhotoSuggestions: suggestions
        )
        await sut.onAppear()
        suggestions.shouldThrow = true

        await sut.onRefresh()

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["report-111", "photo-222_alice"], "a failed refresh must not make the maintainer read the queue as empty")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onReportRowChanged_withRemainingReports_keepsTheRowWithOnlyThose() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [makeReport(barcode: "111"), makeReport(barcode: "111")])
        )
        await sut.onAppear()
        let remaining = [makeReport(barcode: "111")]

        sut.onReportRowChanged(id: "report-111", remaining: remaining)

        guard case .report(_, let reports) = sut.rows.first?.kind else { return XCTFail("the row must stay") }
        XCTAssertEqual(reports.count, 1)
    }

    @MainActor
    func test_onReportRowChanged_withNoRemainingReports_removesTheRow() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [makeReport(barcode: "111")])
        )
        await sut.onAppear()

        sut.onReportRowChanged(id: "report-111", remaining: [])

        XCTAssertTrue(sut.rows.isEmpty)
    }

    @MainActor
    func test_onAppear_sortsRowsOldestFirst_andDatesAReportGroupByItsOldestReport() async {
        let sut = makeSUT(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(stubbedSubmissions: [makeSubmission(id: "s", date: day(2))]),
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [
                makeReport(barcode: "111", date: day(5)),
                makeReport(barcode: "111", date: day(1))
            ]),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [makeSuggestion(itemId: "222", date: day(3))])
        )

        await sut.onAppear()

        XCTAssertEqual(
            sut.rows.map(\.id),
            ["report-111", "submission-s", "photo-222_alice"],
            "the queue is handled oldest first; a report group counts from its oldest report, so days 1 and 5 sort before a suggestion from day 3"
        )
    }

    // MARK: - Helpers

    @MainActor
    private func makeSUT(
        fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol = FetchPendingSubmissionsUseCaseFake(),
        fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol = FetchFoodItemReportsUseCaseFake(),
        fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol = FetchPhotoSuggestionsUseCaseFake()
    ) -> NotificationsViewModel {
        NotificationsViewModel(
            fetchPendingSubmissions: fetchPendingSubmissions,
            fetchFoodItemReports: fetchFoodItemReports,
            fetchPhotoSuggestions: fetchPhotoSuggestions,
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
        )
    }

    private func day(_ number: Int) -> Date {
        Date(timeIntervalSince1970: Double(number) * 86_400)
    }

    private func makeReport(barcode: String, date: Date = .now) -> FoodItemReportDomain {
        FoodItemReportDomain(barcode: barcode, reportedBy: "bob", reason: "wrong data", reportedAt: date)
    }

    private func makeSubmission(id: String, date: Date) -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: "333",
            submittedBy: "carol",
            status: .pending,
            submittedAt: date,
            rejectReason: nil,
            item: FoodItemDomain(
                id: "333",
                kind: .catalogue,
                czName: "Tvaroh",
                engName: "",
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
        )
    }

    private func makeSuggestion(itemId: String, date: Date = .now) -> PhotoSuggestionDomain {
        PhotoSuggestionDomain(
            itemId: itemId,
            suggestedBy: "alice",
            photoURL: URL(fileURLWithPath: "/photo.jpg"),
            suggestedAt: date
        )
    }
}

private final class SwitchableFetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseProtocol {
    var stubbedSuggestions: [PhotoSuggestionDomain]
    var shouldThrow = false

    init(stubbedSuggestions: [PhotoSuggestionDomain]) {
        self.stubbedSuggestions = stubbedSuggestions
    }

    func callAsFunction() async throws -> [PhotoSuggestionDomain] {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedSuggestions
    }
}
