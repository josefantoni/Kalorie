//
//  BodyWeightHistoryViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.10.2026.
//

import XCTest
@testable import Kalorie

final class BodyWeightHistoryViewModelTests: XCTestCase {

    // MARK: - Tests

    func test_onAppear_loadsWeightsNewestFirst() async {
        let (sut, _) = makeSUT(weights: [weight("2026-10-27", 78), weight("2026-10-20", 79)])
        await sut.onAppear()
        XCTAssertEqual(sut.weights.map(\.dayKey), ["2026-10-27", "2026-10-20"])
    }

    func test_chartPoints_onlyIncludeCurrentPeriod_andPagingMovesWindow() async {
        let now = date("2026-10-27")
        let (sut, _) = makeSUT(
            weights: [weight("2026-10-27", 78), weight("2026-10-05", 79), weight("2026-09-15", 80)],
            now: now
        )
        await sut.onAppear()
        XCTAssertEqual(sut.chartPoints.map(\.kilograms), [79, 78])
        sut.onPreviousPeriod()
        XCTAssertEqual(sut.chartPoints.map(\.kilograms), [80])
    }

    func test_paging_cannotGoPastCurrentPeriod() {
        let (sut, _) = makeSUT(weights: [])
        XCTAssertFalse(sut.canPageForward)
        sut.onNextPeriod()
        XCTAssertEqual(sut.periodOffset, 0)
    }

    func test_changingRange_resetsPaging() {
        let (sut, _) = makeSUT(weights: [])
        sut.onPreviousPeriod()
        sut.range = .year
        XCTAssertEqual(sut.periodOffset, 0)
    }

    func test_delete_removesEntry_andReportsChangeOnDisappear() async {
        var changedCount = 0
        let (sut, spy) = makeSUT(weights: [weight("2026-10-27", 78)]) { changedCount += 1 }
        await sut.onAppear()
        sut.onDeleteRequested(sut.weights[0])
        await sut.onDeleteConfirmed()
        XCTAssertEqual(spy.deletedDayKeys, ["2026-10-27"])
        XCTAssertTrue(sut.weights.isEmpty)
        sut.onDisappear()
        sut.onDisappear()
        XCTAssertEqual(changedCount, 1)
    }

    func test_disappear_withoutChanges_doesNotNotifyDashboard() async {
        var changedCount = 0
        let (sut, _) = makeSUT(weights: [weight("2026-10-27", 78)]) { changedCount += 1 }
        await sut.onAppear()
        sut.onDisappear()
        XCTAssertEqual(changedCount, 0)
    }

    func test_edit_savesForTheEntriesOwnDay_neverAnotherDay() async {
        let (sut, spy) = makeSUT(weights: [weight("2026-10-20", 79)])
        await sut.onAppear()
        sut.onEditRequested(sut.weights[0])
        sut.editingKilograms = 77.7
        await sut.onEditConfirmed()
        XCTAssertEqual(spy.savedKilograms, [77.7])
        XCTAssertEqual(spy.savedDayKeys, ["2026-10-20"])
    }

    func test_edit_updatesTheEntryInPlaceWithoutReadingTheWholeHistoryAgain() async {
        let (sut, spy) = makeSUT(weights: [weight("2026-10-27", 78.4), weight("2026-10-26", 79)])
        await sut.onAppear()
        sut.onEditRequested(sut.weights[0])
        sut.editingKilograms = 78
        await sut.onEditConfirmed()
        XCTAssertEqual(sut.weights, [weight("2026-10-27", 78), weight("2026-10-26", 79)])
        XCTAssertEqual(spy.fullReadCount, 1)
    }

    func test_deleteFailure_keepsEntryAndAlerts() async {
        let (sut, _) = makeSUT(weights: [weight("2026-10-27", 78)], deleteShouldThrow: true)
        await sut.onAppear()
        sut.onDeleteRequested(sut.weights[0])
        await sut.onDeleteConfirmed()
        XCTAssertEqual(sut.weights.count, 1)
        XCTAssertNotNil(sut.alertItem)
    }

    // MARK: - Helpers

    private func weight(_ key: String, _ kilograms: Double) -> BodyWeightDomain {
        BodyWeightDomain(dayKey: key, kilograms: kilograms)
    }

    private func date(_ key: String) -> Date {
        BodyWeightDayKey.date(from: key) ?? Date()
    }

    private func makeSUT(
        weights: [BodyWeightDomain],
        now: Date = Date(),
        deleteShouldThrow: Bool = false,
        onChanged: @escaping () -> Void = {}
    ) -> (sut: BodyWeightHistoryViewModel, spy: HistorySpy) {
        let spy = HistorySpy(weights: weights, deleteShouldThrow: deleteShouldThrow)
        let sut = BodyWeightHistoryViewModel(
            unit: .kg,
            fetchBodyWeights: spy,
            saveBodyWeight: spy,
            deleteBodyWeight: spy,
            now: { now },
            onChanged: onChanged
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "BodyWeightHistoryViewModel leaked — potential retain cycle")
        }
        return (sut, spy)
    }
}

private final class HistorySpy: FetchBodyWeightsUseCaseProtocol, SaveBodyWeightUseCaseProtocol, DeleteBodyWeightUseCaseProtocol {

    // MARK: - Properties

    var weights: [BodyWeightDomain]
    let deleteShouldThrow: Bool
    private(set) var deletedDayKeys: [String] = []
    private(set) var savedKilograms: [Double] = []
    private(set) var savedDayKeys: [String] = []
    private(set) var fullReadCount = 0

    // MARK: - Init

    init(weights: [BodyWeightDomain], deleteShouldThrow: Bool) {
        self.weights = weights
        self.deleteShouldThrow = deleteShouldThrow
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [BodyWeightDomain] {
        fullReadCount += 1
        return weights
    }

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] { weights }

    func callAsFunction(kilograms: Double, for day: Date) async throws {
        savedKilograms.append(kilograms)
        savedDayKeys.append(BodyWeightDayKey.make(from: day))
    }

    func callAsFunction(dayKey: String) async throws {
        if deleteShouldThrow { throw URLError(.unknown) }
        deletedDayKeys.append(dayKey)
    }
}
