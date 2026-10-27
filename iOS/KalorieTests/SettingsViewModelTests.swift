//
//  SettingsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class SettingsViewModelTests: XCTestCase {

    @MainActor
    func test_onMove_movingFirstItemToLast_keepsTimeSlotsAtPositions() {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let meal2 = makeMealType(id: 2, name: "C", hour: 16, endHour: 20)
        let sut = makeSUT(mealTypes: [meal0, meal1, meal2])

        sut.onMove(from: IndexSet(integer: 0), to: 3)

        XCTAssertEqual(sut.mealTypes[0].name, "B")
        XCTAssertEqual(sut.mealTypes[0].startMinutes, meal0.startMinutes)
        XCTAssertEqual(sut.mealTypes[1].name, "C")
        XCTAssertEqual(sut.mealTypes[1].startMinutes, meal1.startMinutes)
        XCTAssertEqual(sut.mealTypes[2].name, "A")
        XCTAssertEqual(sut.mealTypes[2].startMinutes, meal2.startMinutes)
    }

    @MainActor
    func test_onMove_movingLastItemToFirst_keepsTimeSlotsAtPositions() {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let meal2 = makeMealType(id: 2, name: "C", hour: 16, endHour: 20)
        let sut = makeSUT(mealTypes: [meal0, meal1, meal2])

        sut.onMove(from: IndexSet(integer: 2), to: 0)

        XCTAssertEqual(sut.mealTypes[0].name, "C")
        XCTAssertEqual(sut.mealTypes[0].startMinutes, meal0.startMinutes)
        XCTAssertEqual(sut.mealTypes[1].name, "A")
        XCTAssertEqual(sut.mealTypes[1].startMinutes, meal1.startMinutes)
        XCTAssertEqual(sut.mealTypes[2].name, "B")
        XCTAssertEqual(sut.mealTypes[2].startMinutes, meal2.startMinutes)
    }

    @MainActor
    func test_onMove_keepsDefaultKeyWithTheMovedMealType() {
        let keyed = MealTypeDomain(id: "0", name: "A", startMinutes: 8 * 60, endMinutes: 12 * 60, defaultKey: "breakfast")
        let sut = makeSUT(mealTypes: [keyed, makeMealType(id: 1, name: "B", hour: 12, endHour: 16)])

        sut.onMove(from: IndexSet(integer: 0), to: 2)

        XCTAssertNil(sut.mealTypes[0].defaultKey)
        XCTAssertEqual(sut.mealTypes[1].defaultKey, "breakfast", "a reorder must not turn a default meal into a user-named one")
    }

    @MainActor
    func test_onMove_setsHasPendingReorder() {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let sut = makeSUT(mealTypes: [meal0, meal1])

        XCTAssertFalse(sut.hasPendingReorder)
        sut.onMove(from: IndexSet(integer: 0), to: 2)
        XCTAssertTrue(sut.hasPendingReorder)
    }

    @MainActor
    func test_onSaveReorder_afterMove_clearsHasPendingReorder() async {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let sut = makeSUT(mealTypes: [meal0, meal1])
        sut.onMove(from: IndexSet(integer: 0), to: 2)

        await sut.onSaveReorder()

        XCTAssertFalse(sut.hasPendingReorder)
    }

    @MainActor
    func test_onSaveReorder_whenUseCaseFails_stillClearsHasPendingReorder() async {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let sut = makeSUT(
            mealTypes: [meal0, meal1],
            updateMealTypeTimes: UpdateMealTypeTimesUseCaseFake(shouldThrow: true)
        )
        sut.onMove(from: IndexSet(integer: 0), to: 2)

        await sut.onSaveReorder()

        XCTAssertFalse(sut.hasPendingReorder)
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onSaveReorder_withoutPendingReorder_doesNotInvokeUseCase() async {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let sut = makeSUT(
            mealTypes: [meal0],
            updateMealTypeTimes: UpdateMealTypeTimesUseCaseFake(shouldThrow: true)
        )

        await sut.onSaveReorder()

        XCTAssertNil(sut.alertItem, "a delete-only edit session has nothing to persist, so Done must be able to close the sheet without a failing network call")
    }

    @MainActor
    func test_onDelete_withSingleMealType_showsAlertAndKeepsIt() async {
        let sut = makeSUT(mealTypes: [makeMealType(id: 0, name: "A", hour: 8, endHour: 12)])
        await sut.onDelete(at: 0)
        XCTAssertNotNil(sut.alertItem)
        XCTAssertEqual(sut.mealTypes.count, 1)
    }

    @MainActor
    func test_onDelete_withMultipleMealTypes_removesCorrectOne() async {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let sut = makeSUT(mealTypes: [meal0, meal1])
        await sut.onDelete(at: 0)
        XCTAssertEqual(sut.mealTypes.count, 1)
        XCTAssertEqual(sut.mealTypes[0].id, "1")
    }

    @MainActor
    func test_onDelete_whenUseCaseFails_showsAlertAndKeepsMealTypes() async {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let sut = makeSUT(
            mealTypes: [meal0, meal1],
            deleteMealType: DeleteMealTypeUseCaseFake(shouldThrow: true)
        )
        await sut.onDelete(at: 0)
        XCTAssertNotNil(sut.alertItem)
        XCTAssertEqual(sut.mealTypes.count, 2)
    }

    func test_onShowAddForm_withExistingMealTypes_setsStartAfterLastEnd() {
        let meal = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let sut = makeSUT(mealTypes: [meal])
        sut.onShowAddForm()
        XCTAssertTrue(sut.isAddFormVisible)
        XCTAssertEqual(Int(sut.newMealStart.minutesSinceMidnight), meal.endMinutes)
    }

    func test_onShowAddForm_withNoMealTypes_makesFormVisible() {
        let sut = makeSUT(mealTypes: [])
        sut.onShowAddForm()
        XCTAssertTrue(sut.isAddFormVisible)
    }

    @MainActor
    func test_onAppear_whenMaintainerClaimIsTrue_setsIsMaintainer() async {
        let sut = makeSUT(fetchMaintainerClaim: FetchMaintainerClaimUseCaseFake(stubbedIsMaintainer: true))
        await sut.onAppear()
        XCTAssertTrue(sut.isMaintainer)
    }

    @MainActor
    func test_onAppear_whenMaintainerClaimIsFalse_leavesIsMaintainerFalse() async {
        let sut = makeSUT(fetchMaintainerClaim: FetchMaintainerClaimUseCaseFake(stubbedIsMaintainer: false))
        await sut.onAppear()
        XCTAssertFalse(sut.isMaintainer)
    }

    @MainActor
    func test_feedbackURL_addressesSupportMailboxWithFeedbackSubject() throws {
        let sut = makeSUT()

        let url = try XCTUnwrap(sut.feedbackURL)
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))

        XCTAssertEqual(components.scheme, "mailto")
        XCTAssertEqual(components.path, Constants.Support.email)
        XCTAssertEqual(components.queryItems?.first { $0.name == "subject" }?.value, L10n.Settings.buttonFeedback)
    }

    // MARK: - Body weight settings

    func test_toggleBodyWeight_savesAndNotifiesDashboard() async {
        var notified: AppSettingsDomain?
        let sut = makeSUT { notified = $0 }
        await sut.onShowBodyWeightChanged(false)
        XCTAssertFalse(sut.appSettings.showBodyWeight)
        XCTAssertEqual(notified?.showBodyWeight, false)
    }

    func test_changeUnit_keepsOtherSettingsAndNotifies() async {
        var notified: AppSettingsDomain?
        let sut = makeSUT(appSettings: AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .kg)) { notified = $0 }
        await sut.onBodyWeightUnitChanged(.lb)
        XCTAssertEqual(notified, AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .lb))
    }

    func test_saveFailure_revertsControlAndAlerts_withoutNotifying() async {
        var notifyCount = 0
        let sut = makeSUT(saveAppSettings: SaveAppSettingsUseCaseFake(shouldThrow: true)) { _ in notifyCount += 1 }
        await sut.onBodyWeightUnitChanged(.lb)
        XCTAssertEqual(sut.appSettings.bodyWeightUnit, .kg)
        XCTAssertNotNil(sut.alertItem)
        XCTAssertEqual(notifyCount, 0)
    }

    func test_overlappingChanges_areSavedInOrder_andALaterSaveCarriesAnEarlierFailedChange() async {
        let save = GatedSaveAppSettings()
        var notified: [AppSettingsDomain] = []
        let sut = makeSUT(saveAppSettings: save) { notified.append($0) }
        let first = Task { await sut.onShowBodyWeightChanged(false) }
        while save.startedCount == 0 { await Task.yield() }
        let second = Task { await sut.onBodyWeightUnitChanged(.lb) }
        try? await Task.sleep(for: .milliseconds(50))
        XCTAssertEqual(save.startedCount, 1, "the second write must wait for the first, or their order on the server is undefined")
        save.releaseFirstWithFailure()
        await first.value
        await second.value
        let expected = AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .lb)
        XCTAssertEqual(save.savedSettings.last, expected)
        XCTAssertEqual(sut.appSettings, expected)
        XCTAssertEqual(notified, [expected])
        XCTAssertNil(sut.alertItem)
    }

    func test_overlappingChanges_whenTheLastSaveFails_revertsToWhatIsStored() async {
        let save = GatedSaveAppSettings(failsSecondCall: true)
        let sut = makeSUT(saveAppSettings: save)
        let first = Task { await sut.onShowBodyWeightChanged(false) }
        while save.startedCount == 0 { await Task.yield() }
        let second = Task { await sut.onBodyWeightUnitChanged(.lb) }
        save.releaseFirstWithSuccess()
        await first.value
        await second.value
        XCTAssertEqual(sut.appSettings, AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .kg))
        XCTAssertNotNil(sut.alertItem)
    }

    func test_unchangedValue_doesNotWrite() async {
        var notifyCount = 0
        let sut = makeSUT { _ in notifyCount += 1 }
        await sut.onBodyWeightUnitChanged(.kg)
        XCTAssertEqual(notifyCount, 0)
    }

    // MARK: - Helpers

    private func makeSUT(
        mealTypes: [MealTypeDomain] = [],
        createMealType: any CreateMealTypeUseCaseProtocol = CreateMealTypeUseCaseFake(),
        deleteMealType: any DeleteMealTypeUseCaseProtocol = DeleteMealTypeUseCaseFake(),
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol = UpdateMealTypeTimesUseCaseFake(),
        fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol = FetchMaintainerClaimUseCaseFake(),
        appSettings: AppSettingsDomain = .defaults,
        saveAppSettings: any SaveAppSettingsUseCaseProtocol = SaveAppSettingsUseCaseFake(),
        onAppSettingsChanged: @escaping (AppSettingsDomain) -> Void = { _ in }
    ) -> SettingsViewModel {
        SettingsViewModel(
            mealTypes: mealTypes,
            appSettings: appSettings,
            onAppSettingsChanged: onAppSettingsChanged,
            saveAppSettings: saveAppSettings,
            createMealType: createMealType,
            deleteMealType: deleteMealType,
            updateMealTypeTimes: updateMealTypeTimes,
            fetchMaintainerClaim: fetchMaintainerClaim
        )
    }

    private func makeMealType(id: Int, name: String, hour: Int, endHour: Int) -> MealTypeDomain {
        MealTypeDomain(id: "\(id)", name: name, startMinutes: hour * 60, endMinutes: endHour * 60)
    }
}

private final class GatedSaveAppSettings: SaveAppSettingsUseCaseProtocol, @unchecked Sendable {

    // MARK: - Properties

    private let lock = NSLock()
    private let failsSecondCall: Bool
    private var continuation: CheckedContinuation<Bool, Never>?
    private var started = 0
    private var saved: [AppSettingsDomain] = []

    var startedCount: Int { lock.withLock { started } }
    var savedSettings: [AppSettingsDomain] { lock.withLock { saved } }

    // MARK: - Init

    init(failsSecondCall: Bool = false) {
        self.failsSecondCall = failsSecondCall
    }

    // MARK: - Functions

    func callAsFunction(_ settings: AppSettingsDomain) async throws {
        let isFirst = lock.withLock { started == 0 }
        if isFirst {
            let succeeds = await withCheckedContinuation { continuation in
                lock.withLock {
                    self.continuation = continuation
                    started += 1
                }
            }
            lock.withLock { saved.append(settings) }
            if !succeeds { throw URLError(.unknown) }
            return
        }
        lock.withLock {
            started += 1
            saved.append(settings)
        }
        if failsSecondCall { throw URLError(.unknown) }
    }

    func releaseFirstWithFailure() {
        lock.withLock { continuation }?.resume(returning: false)
    }

    func releaseFirstWithSuccess() {
        lock.withLock { continuation }?.resume(returning: true)
    }
}
