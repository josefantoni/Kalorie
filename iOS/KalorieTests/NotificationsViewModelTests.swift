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

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["report-r111", "photo-p222"])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenPhotoSuggestionsFail_stillShowsReportsAndAlerts() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [makeReport(barcode: "111")]),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(shouldThrow: true)
        )

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["report-r111"], "one failing query (e.g. rules or index not deployed yet) must not hide what the other sources loaded")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenReportsFail_stillShowsPhotoSuggestionsAndAlerts() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(shouldThrow: true),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [makeSuggestion(itemId: "222")])
        )

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["photo-p222"])
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

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["report-r111", "photo-p222"], "a failed refresh must not make the maintainer read the queue as empty")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_showsOneRowPerReport_evenForTheSameItem() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [
                makeReport(barcode: "111", id: "a"),
                makeReport(barcode: "111", id: "b")
            ])
        )

        await sut.onAppear()

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["report-a", "report-b"], "each report is answered on its own")
    }

    @MainActor
    func test_onRowResolved_forAReport_removesOnlyThatTicketsRow() async {
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [
                makeReport(barcode: "111", id: "a"),
                makeReport(barcode: "111", id: "b")
            ])
        )
        await sut.onAppear()

        sut.onRowResolved(id: "report-a")

        XCTAssertEqual(sut.rows.map(\.id), ["report-b"])
    }

    @MainActor
    func test_onAppear_sortsRowsOldestFirst() async {
        let sut = makeSUT(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(stubbedSubmissions: [makeSubmission(id: "s", date: day(2))]),
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: [
                makeReport(barcode: "111", id: "late", date: day(5)),
                makeReport(barcode: "111", id: "early", date: day(1))
            ]),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: [makeSuggestion(itemId: "222", date: day(3))])
        )

        await sut.onAppear()

        XCTAssertEqual(
            sut.rows.map(\.id),
            ["report-early", "submission-s", "photo-p222", "report-late"],
            "the queue is handled oldest first, each ticket by its own date"
        )
    }

    @MainActor
    func test_onRowResolved_forAPhoto_leavesOtherSuggestionsForTheSameItemWaiting() async {
        let sut = makeSUT(fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: makeSiblingSuggestions()))
        await sut.onAppear()

        sut.onRowResolved(id: "photo-a")

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["photo-b", "photo-c"], "approving one suggestion leaves the other pending ones, and the next approval replaces the photo")
    }

    @MainActor
    func test_onAppear_afterReturningFromADetail_keepsTheRowsResolvedThere() async {
        let sut = makeSUT(fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(stubbedSuggestions: makeSiblingSuggestions()))
        await sut.onAppear()
        sut.onRowResolved(id: "photo-a")

        await sut.onAppear()

        XCTAssertEqual(Set(sut.rows.map(\.id)), ["photo-b", "photo-c"], "popping a pushed detail re-runs .task; a re-fetch would bring back a row the maintainer just resolved")
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
            isMaintainer: true,
            fetchUserNotifications: FetchUserNotificationsUseCaseFake(),
            markUserNotificationSeen: MarkUserNotificationSeenUseCaseFake(),
            hideUserNotification: HideUserNotificationUseCaseFake(),
            withdrawUserNotification: WithdrawUserNotificationUseCaseFake(),
            notificationsStore: UserNotificationsStoreFake()
        )
    }

    private func day(_ number: Int) -> Date {
        Date(timeIntervalSince1970: Double(number) * 86_400)
    }

    private func makeReport(barcode: String, id: String? = nil, date: Date = .now) -> FoodItemReportDomain {
        FoodItemReportDomain(
            id: id ?? "r\(barcode)",
            barcode: barcode,
            reportedBy: "bob",
            reason: "wrong data",
            reportedAt: date,
            status: .pending,
            locale: "en"
        )
    }

    private func makeSubmission(id: String, date: Date) -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: "333",
            submittedBy: "carol",
            status: .pending,
            submittedAt: date,
            rejectReason: nil,
            nameLanguage: "cs",
            item: FoodItemDomain(
                id: "333",
                kind: .catalogue,
                czName: "Tvaroh",
                skName: "Tvaroh",
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

    private func makeSiblingSuggestions() -> [PhotoSuggestionDomain] {
        [
            makeSuggestion(itemId: "111", id: "a", date: day(1)),
            makeSuggestion(itemId: "111", id: "b", date: day(2)),
            makeSuggestion(itemId: "222", id: "c", date: day(3))
        ]
    }

    private func makeSuggestion(itemId: String, id: String? = nil, date: Date = .now) -> PhotoSuggestionDomain {
        PhotoSuggestionDomain(
            id: id ?? "p\(itemId)",
            itemId: itemId,
            suggestedBy: "alice",
            photoURL: URL(fileURLWithPath: "/photo.jpg"),
            suggestedAt: date,
            status: .pending,
            locale: "en"
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
