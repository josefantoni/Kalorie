//
//  ModerationReportsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 17.09.2026.
//

import XCTest
@testable import Kalorie

final class ModerationReportsViewModelTests: XCTestCase {

    // MARK: - onAppear

    @MainActor
    func test_onAppear_groupsReportsByBarcode() async {
        let reports = [
            makeReport(barcode: "111", reportedBy: "user-1"),
            makeReport(barcode: "111", reportedBy: "user-2"),
            makeReport(barcode: "222", reportedBy: "user-3")
        ]
        let sut = makeSUT(fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports))

        await sut.onAppear()

        let group111 = sut.groups.first { $0.barcode == "111" }
        XCTAssertEqual(group111?.reports.count, 2, "two different users reporting the same item must produce one group with two reports, not two rows")
        XCTAssertEqual(sut.groups.count, 2)
    }

    @MainActor
    func test_onAppear_ordersGroupsByReportCountDescending() async {
        let reports = [
            makeReport(barcode: "111", reportedBy: "user-1"),
            makeReport(barcode: "222", reportedBy: "user-2"),
            makeReport(barcode: "222", reportedBy: "user-3"),
            makeReport(barcode: "222", reportedBy: "user-4")
        ]
        let sut = makeSUT(fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports))

        await sut.onAppear()

        XCTAssertEqual(sut.groups.first?.barcode, "222", "the most-reported item is the maintainer's priority signal and must lead the list")
    }

    @MainActor
    func test_onAppear_resolvesItemNameFromCatalogue() async {
        let reports = [makeReport(barcode: "111", reportedBy: "user-1")]
        let item = FoodItemDomain(
            id: "111",
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
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item)
        )

        await sut.onAppear()

        XCTAssertEqual(sut.groups.first?.itemName, item.displayName)
    }

    @MainActor
    func test_onAppear_whenItemNoLongerResolves_stillShowsTheGroup() async {
        let reports = [makeReport(barcode: "111", reportedBy: "user-1")]
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil)
        )

        await sut.onAppear()

        XCTAssertEqual(sut.groups.first?.barcode, "111", "a report on a barcode deleted outside the app must still be clearable, not dropped")
        XCTAssertNil(sut.groups.first?.itemName)
    }

    @MainActor
    func test_onAppear_whenLookupReturnsTheSameItemTwice_stillShowsEveryGroup() async {
        let reports = [
            makeReport(barcode: "111", reportedBy: "user-1"),
            makeReport(barcode: "222", reportedBy: "user-1")
        ]
        let item = FoodItemDomain(
            id: "111",
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
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item)
        )

        await sut.onAppear()

        XCTAssertEqual(sut.groups.count, 2, "a duplicate id from the name lookup must not trap and wipe out the whole queue")
    }

    // MARK: - onResolveTapped

    @MainActor
    func test_onResolveTapped_deletesEveryReportInTheGroupAndRemovesIt() async throws {
        let reports = [
            makeReport(barcode: "111", reportedBy: "user-1"),
            makeReport(barcode: "111", reportedBy: "user-2")
        ]
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            deleteFoodItemReport: deleteFoodItemReport
        )
        await sut.onAppear()
        let group = try XCTUnwrap(sut.groups.first)

        await sut.onResolveTapped(group)

        XCTAssertEqual(deleteFoodItemReport.deletedReportedBy.sorted(), ["user-1", "user-2"])
        XCTAssertTrue(sut.groups.isEmpty)
    }

    @MainActor
    func test_onResolveTapped_whenSomeDeletesFail_keepsOnlyTheFailedReportsAndShowsAlert() async throws {
        let reports = [
            makeReport(barcode: "111", reportedBy: "user-1"),
            makeReport(barcode: "111", reportedBy: "user-2")
        ]
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy(failingReportedBy: ["user-2"])
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            deleteFoodItemReport: deleteFoodItemReport
        )
        await sut.onAppear()
        let group = try XCTUnwrap(sut.groups.first)

        await sut.onResolveTapped(group)

        XCTAssertEqual(
            sut.groups.first?.reports.map(\.reportedBy),
            ["user-2"],
            "a report that failed to delete still exists in Firestore — hiding it would tell the maintainer the item is resolved when it is not"
        )
        XCTAssertNotNil(sut.alertItem, "partial failure is accepted by design 0012 only because it is visible to the maintainer")
    }

    @MainActor
    func test_onResolveTapped_whenEveryDeleteFails_keepsTheGroupAndShowsAlert() async throws {
        let reports = [makeReport(barcode: "111", reportedBy: "user-1")]
        let sut = makeSUT(
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(stubbedReports: reports),
            deleteFoodItemReport: DeleteFoodItemReportUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()
        let group = try XCTUnwrap(sut.groups.first)

        await sut.onResolveTapped(group)

        XCTAssertEqual(sut.groups.first?.reports.count, 1)
        XCTAssertNotNil(sut.alertItem)
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol = FetchFoodItemReportsUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        deleteFoodItemReport: any DeleteFoodItemReportUseCaseProtocol = DeleteFoodItemReportUseCaseFake()
    ) -> ModerationReportsViewModel {
        ModerationReportsViewModel(
            fetchFoodItemReports: fetchFoodItemReports,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            deleteFoodItemReport: deleteFoodItemReport
        )
    }

    private func makeReport(barcode: String, reportedBy: String) -> FoodItemReportDomain {
        FoodItemReportDomain(barcode: barcode, reportedBy: reportedBy, reason: "wrong data", reportedAt: .now)
    }
}

private final class DeleteFoodItemReportUseCaseSpy: DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private(set) var deletedReportedBy: [String] = []
    private let failingReportedBy: Set<String>

    // MARK: - Init

    init(failingReportedBy: Set<String> = []) {
        self.failingReportedBy = failingReportedBy
    }

    // MARK: - Functions

    func callAsFunction(barcode: String, reportedBy: String) async throws {
        if failingReportedBy.contains(reportedBy) { throw URLError(.unknown) }
        deletedReportedBy.append(reportedBy)
    }
}
