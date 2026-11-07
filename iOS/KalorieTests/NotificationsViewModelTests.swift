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
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            deleteFoodItemReport: DeleteFoodItemReportUseCaseFake()
        )
    }

    private func makeReport(barcode: String) -> FoodItemReportDomain {
        FoodItemReportDomain(barcode: barcode, reportedBy: "bob", reason: "wrong data", reportedAt: .now)
    }

    private func makeSuggestion(itemId: String) -> PhotoSuggestionDomain {
        PhotoSuggestionDomain(
            itemId: itemId,
            suggestedBy: "alice",
            photoURL: URL(fileURLWithPath: "/photo.jpg"),
            suggestedAt: .now
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
