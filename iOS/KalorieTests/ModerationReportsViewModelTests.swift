//
//  ModerationReportsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 17.09.2026.
//

import XCTest
@testable import Kalorie

final class ModerationReportsViewModelTests: XCTestCase {

    @MainActor
    func test_onAppear_showsOneRowPerTicket_evenForTheSameItem() async {
        let sut = makeSUT(reports: [
            makeReport(id: "a", barcode: "111"),
            makeReport(id: "b", barcode: "111"),
            makeReport(id: "c", barcode: "222")
        ])

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["a", "b", "c"], "two reports about different fields are answered separately, so they must not be merged into one row")
    }

    @MainActor
    func test_onAppear_resolvesItemNameFromCatalogue() async {
        let item = makePhotoSuggestionItem(id: "111")
        let sut = makeSUT(reports: [makeReport(id: "a", barcode: "111")], item: item)

        await sut.onAppear()

        XCTAssertEqual(sut.rows.first?.itemName, item.displayName)
    }

    @MainActor
    func test_onAppear_whenItemNoLongerResolves_stillShowsTheRow() async {
        let sut = makeSUT(reports: [makeReport(id: "a", barcode: "111")], item: nil)

        await sut.onAppear()

        XCTAssertEqual(sut.rows.first?.report.barcode, "111", "a report on a barcode deleted outside the app must still be answerable, not dropped")
        XCTAssertNil(sut.rows.first?.itemName)
    }

    @MainActor
    func test_onReportResolved_removesOnlyThatTicket() async {
        let sut = makeSUT(reports: [makeReport(id: "a", barcode: "111"), makeReport(id: "b", barcode: "111")])
        await sut.onAppear()

        sut.onReportResolved(id: "a")

        XCTAssertEqual(sut.rows.map(\.id), ["b"], "resolving one report says nothing about the other report of the same item")
    }

    @MainActor
    func test_onAppear_afterReturningFromADetail_keepsTheRowsResolvedThere() async {
        let sut = makeSUT(reports: [makeReport(id: "a", barcode: "111"), makeReport(id: "b", barcode: "222")])
        await sut.onAppear()
        sut.onReportResolved(id: "a")

        await sut.onAppear()

        XCTAssertEqual(sut.rows.map(\.id), ["b"], "popping a pushed detail re-runs .task; a re-fetch would bring back a row the maintainer just resolved")
    }

    // MARK: - Helpers

    private func makeSUT(reports: [FoodItemReportDomain], item: FoodItemDomain? = nil) -> ModerationReportsViewModel {
        ModerationReportsViewModel(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item)
        )
    }
}
