//
//  DashboardViewModelBodyWeightTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 28.10.2026.
//

import XCTest
@testable import Kalorie

final class DashboardViewModelBodyWeightTests: XCTestCase {

    // MARK: - Tests

    func test_weightCircle_isHiddenUntilSettingsLoaded_thenShownByDefault() async {
        let sut = makeSUT()
        XCTAssertFalse(sut.isWeightCircleVisible)
        await sut.onAppear()
        XCTAssertTrue(sut.isWeightCircleVisible)
    }

    func test_weightCircle_staysHiddenWhenTheSettingsReadFails_soADisabledCircleNeverFlashesBack() async {
        let sut = makeSUT(fetchAppSettings: FetchAppSettingsUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        XCTAssertFalse(sut.isWeightCircleVisible)
    }

    func test_weightCircle_isHiddenWhenSettingsTurnItOff() async {
        let settings = AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .kg)
        let sut = makeSUT(fetchAppSettings: FetchAppSettingsUseCaseFake(stubbedSettings: settings))
        await sut.onAppear()
        XCTAssertFalse(sut.isWeightCircleVisible)
    }

    func test_weights_cachedPerDay_secondVisitDoesNotReload() async {
        let fetch = CountingFetchBodyWeights()
        let sut = makeSUT(fetchBodyWeights: fetch)
        await sut.onAppear()
        let yesterday = Date.now.addingTimeInterval(-86_400)
        await sut.onDaySelected(yesterday)
        await sut.onDaySelected(Date.now)
        await sut.onDaySelected(yesterday)
        XCTAssertEqual(fetch.callCount, 2)
    }

    func test_weights_laterEntriesAreOnlyQueriedForDaysBeforeToday() async {
        let fetch = CountingFetchBodyWeights()
        let sut = makeSUT(fetchBodyWeights: fetch)
        await sut.onAppear()
        XCTAssertEqual(fetch.afterCallCount, 0, "nothing can be logged after today, so the query would only cost a read")
        await sut.onDaySelected(Date.now.addingTimeInterval(-3 * 86_400))
        XCTAssertEqual(fetch.afterCallCount, 1)
    }

    func test_weights_laterEntriesAreExposedForAPastDay() async {
        let later = [BodyWeightDomain(dayKey: "2026-10-28", kilograms: 77.9)]
        let sut = makeSUT(fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedLaterWeights: later))
        await sut.onAppear()
        await sut.onDaySelected(Date.now.addingTimeInterval(-3 * 86_400))
        XCTAssertEqual(sut.laterWeights, later)
    }

    func test_weights_afterWrite_cacheIsClearedAndSelectedDayReloaded() async {
        let fetch = CountingFetchBodyWeights()
        let sut = makeSUT(fetchBodyWeights: fetch)
        await sut.onAppear()
        await sut.onWeightsChanged()
        XCTAssertEqual(fetch.callCount, 2)
    }

    func test_weights_loadFailure_doesNotShowThePreviousDaysWeight() async {
        let todayEntry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: .now), kilograms: 78.4)
        let sut = makeSUT(fetchBodyWeights: FailingAfterFirstFetchBodyWeights(firstResult: [todayEntry]))
        await sut.onAppear()
        XCTAssertEqual(sut.recentWeights, [todayEntry])
        await sut.onDaySelected(Date.now.addingTimeInterval(-86_400))
        XCTAssertTrue(sut.recentWeights.isEmpty)
        XCTAssertTrue(sut.laterWeights.isEmpty)
        XCTAssertFalse(sut.hasWeightForSelectedDay)
    }

    func test_weights_failedLoadIsNotCached_soTheNextForegroundLoadsThemAgain() async {
        let todayEntry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: .now), kilograms: 78.4)
        let sut = makeSUT(fetchBodyWeights: FailingFirstFetchBodyWeights(laterResult: [todayEntry]))
        await sut.onAppear()
        XCTAssertFalse(sut.canLogWeight)
        await sut.onForeground()
        XCTAssertEqual(sut.recentWeights, [todayEntry], "a failed read must not be remembered as an empty window")
        XCTAssertTrue(sut.canLogWeight)
    }

    func test_weights_failedReloadAfterAWrite_hidesTheStaleWindow_soTheBadgeDoesNotOfferToOverwriteIt() async {
        let olderEntry = BodyWeightDomain(dayKey: "2026-10-01", kilograms: 81.3)
        let sut = makeSUT(fetchBodyWeights: FailingAfterFirstFetchBodyWeights(firstResult: [olderEntry]))
        await sut.onAppear()
        XCTAssertTrue(sut.canLogWeight)
        await sut.onWeightsChanged()
        XCTAssertTrue(sut.recentWeights.isEmpty)
        XCTAssertTrue(sut.laterWeights.isEmpty)
        XCTAssertFalse(sut.canLogWeight, "the window predates the write or delete, so seeding the picker from it could overwrite a real entry")
    }

    func test_weights_failedRefresh_keepsTheWindowItDidNotInvalidateByAWrite() async {
        let todayEntry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: .now), kilograms: 78.4)
        let sut = makeSUT(fetchBodyWeights: FailingAfterFirstFetchBodyWeights(firstResult: [todayEntry]))
        await sut.onAppear()
        await sut.onRefresh()
        XCTAssertEqual(sut.recentWeights, [todayEntry])
        XCTAssertTrue(sut.canLogWeight)
    }

    func test_weightBadge_today_isAvailableWhetherOrNotAWeightIsLogged() async {
        let sut = makeSUT()
        await sut.onAppear()
        XCTAssertTrue(sut.canLogWeight)
    }

    func test_weightBadge_whenTheWeightsCouldNotBeLoaded_isUnavailable_soARealEntryIsNotOverwrittenWithTheDefault() async {
        let sut = makeSUT(fetchBodyWeights: FetchBodyWeightsUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        XCTAssertFalse(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        XCTAssertFalse(sut.isWeightPopoverVisible)
    }

    func test_weightBadge_pastDayWithoutEntry_isAvailable_soAMissedDayCanBeBackfilled() async {
        let earlierEntry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: .now.addingTimeInterval(-10 * 86_400)), kilograms: 79.5)
        let sut = makeSUT(fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedWeights: [earlierEntry]))
        await sut.onAppear()
        await sut.onDaySelected(Date.now.addingTimeInterval(-3 * 86_400))
        XCTAssertFalse(sut.hasWeightForSelectedDay)
        XCTAssertTrue(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        XCTAssertTrue(sut.isWeightPopoverVisible)
        XCTAssertEqual(sut.weightPickerKilograms, 79.5, "the picker starts from the weight valid on that day")
    }

    func test_weightBadge_pastDayWithEntry_staysAvailableToEditThatEntry() async {
        let day = Date.now.addingTimeInterval(-3 * 86_400)
        let entry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: day), kilograms: 80)
        let sut = makeSUT(fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedWeights: [entry]))
        await sut.onAppear()
        await sut.onDaySelected(day)
        XCTAssertTrue(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        XCTAssertTrue(sut.isWeightPopoverVisible)
        XCTAssertEqual(sut.weightPickerKilograms, 80)
    }

    func test_weightBadge_futureDay_isNotAvailableAndDoesNotOpenPicker() async {
        let sut = makeSUT()
        await sut.onAppear()
        await sut.onDaySelected(Date.now.addingTimeInterval(3 * 86_400))
        XCTAssertFalse(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        XCTAssertFalse(sut.isWeightPopoverVisible)
    }

    func test_weightBadge_dayBeforeFirstYear_isNotAvailableAndDoesNotOpenPicker() async {
        let sut = makeSUT()
        await sut.onAppear()
        await sut.onDaySelected(Date(timeIntervalSince1970: 1_766_000_000))
        XCTAssertFalse(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        XCTAssertFalse(sut.isWeightPopoverVisible)
    }

    func test_weightPicker_initialValue_isWeightValidOnDayElseDefault() async {
        let withWeight = makeSUT(fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedWeights: [BodyWeightDomain(dayKey: "2026-10-01", kilograms: 81.3)]))
        await withWeight.onAppear()
        withWeight.onWeightBadgeTapped()
        XCTAssertEqual(withWeight.weightPickerKilograms, 81.3)

        let empty = makeSUT()
        await empty.onAppear()
        empty.onWeightBadgeTapped()
        XCTAssertEqual(empty.weightPickerKilograms, 70)
    }

    func test_weightSave_failure_keepsPickerOpenAndAlerts() async {
        let sut = makeSUT(saveBodyWeight: SaveBodyWeightUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        sut.onWeightBadgeTapped()
        await sut.onWeightConfirmed()
        XCTAssertTrue(sut.isWeightPopoverVisible)
        XCTAssertNotNil(sut.alertItem)
    }

    func test_weightSave_writesToTheDayThePickerWasOpenedOn_evenWhenTheSelectedDayMovesMeanwhile() async {
        let save = RecordingSaveBodyWeight()
        let sut = makeSUT(saveBodyWeight: save)
        await sut.onAppear()
        let opened = sut.selectedDay
        sut.onWeightBadgeTapped()
        await sut.onDaySelected(opened.addingTimeInterval(-86_400))
        await sut.onWeightConfirmed()
        XCTAssertEqual(save.days, [opened])
    }

    func test_weights_stillLoad_whenMealTypesFailAtLaunch_soTheBadgeDoesNotOfferToOverwriteARealEntry() async {
        let entry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: .now), kilograms: 78.4)
        let sut = makeSUT(
            fetchBodyWeights: SlowFetchBodyWeights(result: [entry]),
            fetchMealTypes: FetchMealTypesUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()
        XCTAssertEqual(sut.recentWeights, [entry])
        XCTAssertTrue(sut.hasWeightForSelectedDay)
        XCTAssertTrue(sut.isWeightCircleVisible)
    }

    func test_weights_cachedWindowOfAnotherDay_isNotShownOnTheSelectedDay() async {
        let today = Date.now
        let yesterday = today.addingTimeInterval(-86_400)
        let sut = makeSUT(fetchBodyWeights: PerDayFetchBodyWeights())
        await sut.onAppear()
        await sut.onDaySelected(yesterday)
        await sut.onDaySelected(today)
        await sut.onDayChanged(yesterday)
        XCTAssertEqual(sut.recentWeights.first?.dayKey, BodyWeightDayKey.make(from: today))
    }

    func test_weights_whileANewDayLoads_thePreviousDaysWindowIsNotShownAndTheBadgeIsUnavailable() async {
        let today = Date.now
        let todayEntry = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: today), kilograms: 78.4)
        let fetch = GatedSecondFetchBodyWeights(firstResult: [todayEntry])
        let sut = makeSUT(fetchBodyWeights: fetch)
        await sut.onAppear()
        XCTAssertTrue(sut.canLogWeight)
        let yesterday = today.addingTimeInterval(-86_400)
        let selection = Task { await sut.onDaySelected(yesterday) }
        while !fetch.isSecondFetchWaiting { await Task.yield() }
        XCTAssertTrue(sut.recentWeights.isEmpty, "yesterday must not be drawn with today's weight")
        XCTAssertFalse(sut.hasWeightForSelectedDay)
        XCTAssertFalse(sut.canLogWeight, "the picker must not be seeded from a window that belongs to another day")
        fetch.releaseSecondFetch()
        await selection.value
    }

    func test_weights_staleLoadFinishingAfterAWrite_doesNotOverwriteTheFreshWindow() async {
        let saved = BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: .now), kilograms: 79.0)
        let fetch = GatedFirstFetchBodyWeights(freshResult: [saved])
        let sut = makeSUT(fetchBodyWeights: fetch)
        let launch = Task { await sut.onAppear() }
        while !fetch.isFirstFetchWaiting { await Task.yield() }
        await sut.onWeightsChanged()
        XCTAssertEqual(sut.recentWeights, [saved])
        fetch.releaseFirstFetch()
        await launch.value
        XCTAssertEqual(sut.recentWeights, [saved], "a load that started before the write must not replace the window read after it")
    }

    func test_settings_staleReadFinishingAfterAChange_doesNotOverwriteTheNewerSetting() async {
        let stale = AppSettingsDomain(showBodyWeight: true, bodyWeightUnit: .kg)
        let changed = AppSettingsDomain(showBodyWeight: true, bodyWeightUnit: .lb)
        let fetch = GatedFetchAppSettings(result: stale)
        let sut = makeSUT(fetchAppSettings: fetch)
        let launch = Task { await sut.onAppear() }
        while !fetch.isWaiting { await Task.yield() }
        sut.onAppSettingsChanged(changed)
        fetch.release()
        await launch.value
        XCTAssertEqual(sut.appSettings, changed, "a read that started before the user changed a setting must not bring the old value back")
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol = FetchBodyWeightsUseCaseFake(),
        fetchAppSettings: any FetchAppSettingsUseCaseProtocol = FetchAppSettingsUseCaseFake(),
        fetchMealTypes: any FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        saveBodyWeight: any SaveBodyWeightUseCaseProtocol = SaveBodyWeightUseCaseFake()
    ) -> DashboardViewModel {
        let sut = DashboardViewModel(
            fetchMealTypes: fetchMealTypes,
            fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(),
            fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseFake(),
            setupDefaultMeals: SetupDefaultMealsUseCaseFake(),
            confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseFake(stubbedResult: true),
            deleteFoodConsumed: DeleteFoodConsumedUseCaseFake(),
            copyFoodsConsumed: CopyFoodsConsumedUseCaseFake(),
            assignFoodMealType: AssignFoodMealTypeUseCaseFake(),
            fetchBodyWeights: fetchBodyWeights,
            fetchAppSettings: fetchAppSettings,
            saveBodyWeight: saveBodyWeight,
            authProvider: AuthProviderFake(),
            signInSpotlightStore: SignInSpotlightStoreFake()
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "DashboardViewModel leaked — potential retain cycle")
        }
        return sut
    }
}

private final class RecordingSaveBodyWeight: SaveBodyWeightUseCaseProtocol {

    // MARK: - Properties

    private(set) var days: [Date] = []

    // MARK: - Functions

    func callAsFunction(kilograms: Double, for day: Date) async throws {
        days.append(day)
    }
}

private final class FailingFirstFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private let laterResult: [BodyWeightDomain]
    private var hasFailed = false

    // MARK: - Init

    init(laterResult: [BodyWeightDomain]) {
        self.laterResult = laterResult
    }

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        guard hasFailed else {
            hasFailed = true
            throw URLError(.unknown)
        }
        return laterResult
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}

private final class FailingAfterFirstFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private let firstResult: [BodyWeightDomain]
    private var hasServedFirstResult = false

    // MARK: - Init

    init(firstResult: [BodyWeightDomain]) {
        self.firstResult = firstResult
    }

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        guard !hasServedFirstResult else { throw URLError(.unknown) }
        hasServedFirstResult = true
        return firstResult
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}

private final class CountingFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0
    private(set) var afterCallCount = 0

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        callCount += 1
        return []
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] {
        afterCallCount += 1
        return []
    }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}

private final class SlowFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private let result: [BodyWeightDomain]

    // MARK: - Init

    init(result: [BodyWeightDomain]) {
        self.result = result
    }

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        try await Task.sleep(for: .milliseconds(50))
        return result
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}

private final class GatedFetchAppSettings: FetchAppSettingsUseCaseProtocol {

    // MARK: - Properties

    private let result: AppSettingsDomain
    private var continuation: CheckedContinuation<Void, Never>?
    private(set) var isWaiting = false

    // MARK: - Init

    init(result: AppSettingsDomain) {
        self.result = result
    }

    // MARK: - Functions

    func release() {
        continuation?.resume()
        continuation = nil
    }

    func callAsFunction() async throws -> AppSettingsDomain {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            isWaiting = true
        }
        return result
    }
}

private final class GatedFirstFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private let freshResult: [BodyWeightDomain]
    private var callCount = 0
    private var firstFetchContinuation: CheckedContinuation<Void, Never>?
    private(set) var isFirstFetchWaiting = false

    // MARK: - Init

    init(freshResult: [BodyWeightDomain]) {
        self.freshResult = freshResult
    }

    // MARK: - Functions

    func releaseFirstFetch() {
        firstFetchContinuation?.resume()
        firstFetchContinuation = nil
    }

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        callCount += 1
        guard callCount == 1 else { return freshResult }
        await withCheckedContinuation { continuation in
            firstFetchContinuation = continuation
            isFirstFetchWaiting = true
        }
        return []
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}

private final class GatedSecondFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Properties

    private let firstResult: [BodyWeightDomain]
    private var callCount = 0
    private var secondFetchContinuation: CheckedContinuation<Void, Never>?
    private(set) var isSecondFetchWaiting = false

    // MARK: - Init

    init(firstResult: [BodyWeightDomain]) {
        self.firstResult = firstResult
    }

    // MARK: - Functions

    func releaseSecondFetch() {
        secondFetchContinuation?.resume()
        secondFetchContinuation = nil
    }

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        callCount += 1
        guard callCount == 2 else { return firstResult }
        await withCheckedContinuation { continuation in
            secondFetchContinuation = continuation
            isSecondFetchWaiting = true
        }
        return []
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}

private final class PerDayFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        [BodyWeightDomain(dayKey: BodyWeightDayKey.make(from: day), kilograms: 80)]
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}
