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
        await sut.onPreviousPeriod()
        XCTAssertEqual(sut.chartPoints.map(\.kilograms), [80])
    }

    func test_onAppear_readsOnlyTheCurrentYear_soALongHistoryCostsNothingUntilItIsPagedTo() async {
        let (sut, spy) = makeSUT(
            weights: [weight("2026-10-27", 78), weight("2025-12-30", 80), weight("2024-05-01", 82)],
            now: date("2026-10-27")
        )
        await sut.onAppear()
        XCTAssertEqual(spy.requestedYears, [2026])
        XCTAssertEqual(sut.weights.map(\.dayKey), ["2026-10-27"])
    }

    func test_paging_withinTheLoadedYear_readsNothingMore() async {
        let (sut, spy) = makeSUT(weights: [weight("2026-10-27", 78), weight("2026-09-15", 80)], now: date("2026-10-27"))
        await sut.onAppear()
        await sut.onPreviousPeriod()
        XCTAssertEqual(spy.requestedYears, [2026])
    }

    func test_paging_acrossTheYearBoundary_readsThePreviousYearOnce() async {
        let (sut, spy) = makeSUT(
            weights: [weight("2027-01-10", 78), weight("2026-12-20", 80), weight("2026-11-02", 81), weight("2025-05-01", 82)],
            now: date("2027-01-15")
        )
        await sut.onAppear()
        await sut.onPreviousPeriod()
        XCTAssertEqual(spy.requestedYears, [2027, 2026])
        XCTAssertEqual(sut.chartPoints.map(\.kilograms), [80])
        await sut.onPreviousPeriod()
        XCTAssertEqual(spy.requestedYears, [2027, 2026], "November is in the year that is already loaded")
        XCTAssertEqual(sut.weights.map(\.dayKey), ["2027-01-10", "2026-12-20", "2026-11-02"])
    }

    func test_isLoadingEarlierYear_isOnlyTrueWhileAnEarlierYearIsBeingRead() async {
        let (sut, _) = makeSUT(weights: [weight("2027-01-10", 78), weight("2026-12-20", 80)], now: date("2027-01-15"))
        await sut.onAppear()
        XCTAssertFalse(sut.isLoadingEarlierYear)
        await sut.onPreviousPeriod()
        XCTAssertFalse(sut.isLoadingEarlierYear, "it must be reset after the read, whether it succeeded or not")
    }

    func test_changingToYearRange_readsOnlyWhatTheCurrentPeriodNeeds() async {
        let (sut, spy) = makeSUT(weights: [weight("2026-10-27", 78), weight("2025-12-30", 80)], now: date("2026-10-27"))
        await sut.onAppear()
        sut.range = .year
        await sut.loadEarlierYearsIfNeeded()
        XCTAssertEqual(spy.requestedYears, [2026])
    }

    func test_failedEarlierYear_alertsAndIsRetriedByTheNextRequest() async {
        let (sut, spy) = makeSUT(weights: [weight("2027-01-10", 78), weight("2026-12-20", 80)], now: date("2027-01-15"))
        await sut.onAppear()
        spy.failingYears = [2026]
        await sut.onPreviousPeriod()
        XCTAssertNotNil(sut.alertItem)
        spy.failingYears = []
        await sut.loadEarlierYearsIfNeeded()
        XCTAssertEqual(sut.weights.map(\.dayKey), ["2027-01-10", "2026-12-20"])
    }

    func test_failedFirstRead_showsErrorStateInsteadOfEmptyState_andRetryLoads() async {
        let (sut, spy) = makeSUT(weights: [weight("2026-10-20", 79)], now: date("2026-10-27"))
        spy.failingYears = [2026]
        await sut.onAppear()
        guard case .error = sut.state else { return XCTFail("expected the error state") }
        XCTAssertTrue(sut.weights.isEmpty)
        spy.failingYears = []
        await sut.onRetryTapped()
        guard case .loaded = sut.state else { return XCTFail("expected the loaded state") }
        XCTAssertEqual(sut.weights.map(\.dayKey), ["2026-10-20"])
    }

    func test_paging_cannotGoPastCurrentPeriod() {
        let (sut, _) = makeSUT(weights: [])
        XCTAssertFalse(sut.canPageForward)
        sut.onNextPeriod()
        XCTAssertEqual(sut.periodOffset, 0)
    }

    func test_paging_stopsAtTheFirstYear_andReadsNothingBeforeIt() async {
        let (sut, spy) = makeSUT(weights: [weight("2026-01-10", 78), weight("2025-12-20", 80)], now: date("2026-01-15"))
        await sut.onAppear()
        XCTAssertFalse(sut.canPageBack)
        await sut.onPreviousPeriod()
        XCTAssertEqual(sut.periodOffset, 0)
        XCTAssertEqual(spy.requestedYears, [2026])
    }

    func test_paging_inTheFirstYear_canGoBackUntilItsFirstMonth() async {
        let (sut, _) = makeSUT(weights: [weight("2026-03-10", 78)], now: date("2026-03-15"))
        await sut.onAppear()
        await sut.onPreviousPeriod()
        await sut.onPreviousPeriod()
        XCTAssertEqual(sut.periodOffset, -2)
        XCTAssertFalse(sut.canPageBack)
        await sut.onPreviousPeriod()
        XCTAssertEqual(sut.periodOffset, -2)
    }

    func test_opensOnThePeriodOfTheSelectedDay_notOnTheCurrentOne() async {
        let (sut, _) = makeSUT(
            weights: [weight("2026-11-01", 77), weight("2026-10-28", 78), weight("2026-10-05", 79)],
            now: date("2026-11-01"),
            selectedDay: date("2026-10-28")
        )
        await sut.onAppear()
        XCTAssertEqual(sut.periodOffset, -1)
        XCTAssertEqual(sut.chartPoints.map(\.kilograms), [79, 78])
        XCTAssertTrue(sut.canPageForward)
    }

    func test_selectedDayInAnEarlierYear_readsThatYearOnce() async {
        let (sut, spy) = makeSUT(
            weights: [weight("2027-02-01", 77), weight("2026-12-10", 78)],
            now: date("2027-02-01"),
            selectedDay: date("2026-12-10")
        )
        await sut.onAppear()
        XCTAssertEqual(spy.requestedYears, [2027, 2026])
        XCTAssertEqual(sut.chartPoints.map(\.kilograms), [78])
    }

    func test_changingRange_keepsTheSelectedDayInView() async {
        let (sut, _) = makeSUT(weights: [], now: date("2026-11-11"), selectedDay: date("2026-10-28"))
        sut.range = .year
        XCTAssertEqual(sut.periodOffset, 0)
        sut.range = .month
        XCTAssertEqual(sut.periodOffset, -1)
        sut.range = .week
        XCTAssertEqual(sut.periodOffset, -2)
    }

    func test_changingRange_resetsPaging() async {
        let (sut, _) = makeSUT(weights: [])
        await sut.onPreviousPeriod()
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
        selectedDay: Date? = nil,
        deleteShouldThrow: Bool = false,
        onChanged: @escaping () -> Void = {}
    ) -> (sut: BodyWeightHistoryViewModel, spy: HistorySpy) {
        let spy = HistorySpy(weights: weights, deleteShouldThrow: deleteShouldThrow)
        let sut = BodyWeightHistoryViewModel(
            unit: .kg,
            selectedDay: selectedDay ?? now,
            fetchBodyWeights: spy,
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

private final class HistorySpy: FetchBodyWeightsUseCaseProtocol, DeleteBodyWeightUseCaseProtocol {

    // MARK: - Properties

    var weights: [BodyWeightDomain]
    let deleteShouldThrow: Bool
    private(set) var deletedDayKeys: [String] = []
    private(set) var requestedYears: [Int] = []
    var failingYears: Set<Int> = []

    // MARK: - Init

    init(weights: [BodyWeightDomain], deleteShouldThrow: Bool) {
        self.weights = weights
        self.deleteShouldThrow = deleteShouldThrow
    }

    // MARK: - Functions

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] {
        requestedYears.append(year)
        if failingYears.contains(year) { throw URLError(.unknown) }
        return weights.filter { $0.dayKey.hasPrefix(String(format: "%04d-", year)) }
    }

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] { weights }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { weights }

    func callAsFunction(dayKey: String) async throws {
        if deleteShouldThrow { throw URLError(.unknown) }
        deletedDayKeys.append(dayKey)
    }
}
