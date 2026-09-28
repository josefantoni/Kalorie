//
//  DashboardViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class DashboardViewModelTests: XCTestCase {

    // MARK: - DailyMacros

    func test_dailyMacros_whenAFoodsFiberIsUnknown_showsZeroInsteadOfExcludingIt() {
        let macros = DailyMacros(foods: [makeFood(id: "1", hour: 8, fiber: nil), makeFood(id: "2", hour: 9, fiber: 3)])
        XCTAssertEqual(macros.fiber, 3)
    }

    // MARK: - groupedFoods — no foods

    func test_groupedFoods_withNoFoodsConsumed_returnsEmpty() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = []
        XCTAssertTrue(sut.groupedFoods.isEmpty)
    }

    // MARK: - groupedFoods — assignment

    func test_groupedFoods_foodWithinRange_isAssignedToMealType() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 10)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0")
        XCTAssertEqual(groups[0].foods.first?.id, "f1")
    }

    func test_groupedFoods_foodAtExactStartTime_isIncluded() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 8, minute: 0)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0")
    }

    func test_groupedFoods_foodAtExactEndTime_isExcluded() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 12, minute: 0)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertNil(groups[0].mealType)
        XCTAssertEqual(groups[0].foods.first?.id, "f1")
    }

    func test_groupedFoods_foodOutsideAllRanges_goesToNilGroup() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 7)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertNil(groups[0].mealType)
    }

    // MARK: - groupedFoods — ordering

    func test_groupedFoods_nilGroupAppearsLast() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "assigned", hour: 10), makeFood(id: "unassigned", hour: 7)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 2)
        XCTAssertNotNil(groups[0].mealType)
        XCTAssertNil(groups[1].mealType)
    }

    func test_groupedFoods_sortsMealTypeGroupsByStartTime() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 1, hour: 12, endHour: 16), makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "early", hour: 9), makeFood(id: "late", hour: 13)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 2)
        XCTAssertEqual(groups[0].mealType?.id, "0")
        XCTAssertEqual(groups[1].mealType?.id, "1")
    }

    func test_groupedFoods_foodInOverlappingWindows_isAssignedToEarlierWindowOnly() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 14), makeMealType(id: 1, hour: 12, endHour: 16)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 13)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0")
        XCTAssertEqual(groups[0].foods.map(\.id), ["f1"])
    }

    func test_groupedFoods_wrappingMealType_includesFoodAfterMidnight() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 23, endHour: 1)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 0, minute: 30)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0")
    }

    // MARK: - groupedFoods — pinning (ADR 0022)

    func test_groupedFoods_pinnedFood_isAssignedToPinnedMealTypeRegardlessOfTime_andKeepsItsLoggedDate() {
        let sut = makeSUT()
        sut.mealTypes = [
            makeMealType(id: 0, hour: 7, endHour: 10),
            makeMealType(id: 1, hour: 18, endHour: 21)
        ]
        let loggedAt22 = makeFood(id: "f1", hour: 22, mealTypeId: "0")
        sut.foodsConsumed = [loggedAt22]

        let groups = sut.groupedFoods

        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0", "a pin must move the entry into its meal section even though 22:00 falls in neither window")
        XCTAssertEqual(groups[0].foods.first?.date, loggedAt22.date, "the pin must change the section only — the logged timestamp stays untouched")
    }

    func test_groupedFoods_pinnedFood_overridesAWindowItsOwnTimeWouldOtherwiseFallInto() {
        let sut = makeSUT()
        sut.mealTypes = [
            makeMealType(id: 0, hour: 8, endHour: 12),
            makeMealType(id: 1, hour: 12, endHour: 16)
        ]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 9, mealTypeId: "1")]

        let groups = sut.groupedFoods

        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "1", "the pin must win even when the entry's own time falls inside a different window")
    }

    func test_groupedFoods_unknownPinnedMealTypeId_fallsBackToWindowAssignment() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 9, mealTypeId: "deleted-meal-type")]

        let groups = sut.groupedFoods

        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0", "a pin naming a meal type that no longer exists must be treated as no pin, not as a dead-end")
    }

    func test_groupedFoods_unknownPinnedMealTypeId_fallsBackToUnassignedWhenNoWindowMatches() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 22, mealTypeId: "deleted-meal-type")]

        let groups = sut.groupedFoods

        XCTAssertEqual(groups.count, 1)
        XCTAssertNil(groups[0].mealType, "an unresolvable pin with no matching window must land in the unassigned section, not vanish")
        XCTAssertEqual(groups[0].foods.map(\.id), ["f1"])
    }

    func test_groupedFoods_mealTypeWithNoMatchingFoods_isOmitted() {
        let sut = makeSUT()
        sut.mealTypes = [
            makeMealType(id: 0, hour: 8, endHour: 12),
            makeMealType(id: 1, hour: 12, endHour: 16)
        ]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 9)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, "0")
    }

    // MARK: - onAppear

    @MainActor
    func test_onAppear_whenMealTypesEmpty_callsSetupDefaultMeals() async {
        let sut = makeSUT(
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: []),
            setupDefaultMeals: SetupDefaultMealsUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 8, endHour: 12)])
        )
        await sut.onAppear()
        XCTAssertFalse(sut.mealTypes.isEmpty)
    }

    @MainActor
    func test_onAppear_whenFetchSucceeds_setsLoadedStateAndNoAlert() async {
        let sut = makeSUT(
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 8, endHour: 12)])
        )
        await sut.onAppear()
        XCTAssertFalse(sut.state.isLoading)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_calledAgainAfterInitialLoad_doesNotResetSelectedDayOrFoods() async {
        let sut = makeSUT(fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 8, endHour: 12)]))
        await sut.onAppear()
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: .now) ?? .now
        await sut.onDaySelected(yesterday)
        sut.foodsConsumed = [makeFood(id: "f1", hour: 10)]

        await sut.onAppear()

        XCTAssertTrue(
            Calendar.current.isDate(sut.selectedDay, inSameDayAs: yesterday),
            "SwiftUI re-runs .task { onAppear() } when a pushed detail view is popped back to the Dashboard; a second onAppear must not silently jump the user back to today"
        )
        XCTAssertEqual(sut.foodsConsumed.map(\.id), ["f1"])
    }

    @MainActor
    func test_onAppear_whenFetchFails_showsAlert() async {
        let sut = makeSUT(fetchMealTypes: FetchMealTypesUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenFetchFailsOffline_showsOfflineAlert() async {
        let offlineError = FirestoreDataProviderError.unreachable
        let sut = makeSUT(fetchMealTypes: FetchMealTypesUseCaseFake(shouldThrow: true, errorToThrow: offlineError))
        await sut.onAppear()
        XCTAssertEqual(sut.alertItem?.title, L10n.Common.errorOffline, "a Firestore unavailable error must be distinguishable from any other failure")
        XCTAssertEqual(sut.alertItem?.message, L10n.Common.errorOfflineMessage, "the offline alert must tell the user what to do about it, not only what happened")
    }

    @MainActor
    func test_onAppear_whenFetchFailsWithOtherError_showsUnknownErrorAlert() async {
        let sut = makeSUT(fetchMealTypes: FetchMealTypesUseCaseFake(shouldThrow: true, errorToThrow: URLError(.unknown)))
        await sut.onAppear()
        XCTAssertEqual(sut.alertItem?.title, L10n.Common.errorUnknown, "a non-offline error must not be mistaken for offline")
        XCTAssertEqual(sut.alertItem?.message, L10n.Common.errorUnknownMessage, "the unknown-error alert must carry a body line too, so AlertItem.message has a producer")
    }

    @MainActor
    func test_onAppear_whenMealTypesEmptyButNotConfirmedByServer_doesNotCallSetupDefaultMeals() async {
        let sut = makeSUT(
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: []),
            setupDefaultMeals: SetupDefaultMealsUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 8, endHour: 12)]),
            confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseFake(stubbedError: URLError(.notConnectedToInternet))
        )
        await sut.onAppear()
        XCTAssertTrue(sut.mealTypes.isEmpty)
        XCTAssertNotNil(sut.alertItem)
    }

    // MARK: - onRefresh

    @MainActor
    func test_onRefresh_beforeInitialLoadCompletes_doesNothing() async {
        let sut = makeSUT(fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 8, endHour: 12)]))
        await sut.onRefresh()
        XCTAssertTrue(sut.mealTypes.isEmpty, "a day-change notification racing the cold-launch load must not run its own fetch on top of onAppear's")
    }

    @MainActor
    func test_onRefresh_afterInitialLoadCompletes_refetches() async {
        let sut = makeSUT(fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 8, endHour: 12)]))
        await sut.onAppear()
        await sut.onRefresh()
        XCTAssertFalse(sut.mealTypes.isEmpty)
    }

    // MARK: - delete

    @MainActor
    func test_onDeleteRequested_showsConfirmation() {
        let sut = makeSUT()
        XCTAssertFalse(sut.isDeleteConfirmationVisible)
        sut.onDeleteRequested(makeFood(id: "f1", hour: 8))
        XCTAssertTrue(sut.isDeleteConfirmationVisible)
    }

    @MainActor
    func test_onDeleteConfirmed_withoutPendingRequest_doesNothing() async {
        let sut = makeSUT()
        sut.foodsConsumed = [makeFood(id: "f1", hour: 8)]

        await sut.onDeleteConfirmed()

        XCTAssertEqual(sut.foodsConsumed.map(\.id), ["f1"])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteConfirmed_whenDeleteSucceeds_reloadsFoodsFromServer() async {
        let remaining = makeFood(id: "f2", hour: 9)
        let toDelete = makeFood(id: "f1", hour: 8)
        let sut = makeSUT(fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods: [remaining]))
        sut.foodsConsumed = [toDelete, remaining]

        sut.onDeleteRequested(toDelete)
        await sut.onDeleteConfirmed()

        XCTAssertEqual(sut.foodsConsumed.map(\.id), ["f2"], "a confirmed delete must refetch the day so the removed entry disappears")
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onDeleteConfirmed_whenDeleteFails_showsAlertAndKeepsExistingFoods() async {
        let existing = makeFood(id: "f1", hour: 8)
        let sut = makeSUT(deleteFoodConsumed: DeleteFoodConsumedUseCaseFake(shouldThrow: true))
        sut.foodsConsumed = [existing]

        sut.onDeleteRequested(existing)
        await sut.onDeleteConfirmed()

        XCTAssertEqual(sut.foodsConsumed.map(\.id), ["f1"], "a failed delete must not silently drop the entry from the list")
        XCTAssertNotNil(sut.alertItem)
    }

    // MARK: - Copy — defaults

    func test_onCopyRequested_opensTheBoxOnTodayAndTheWindowTheCurrentTimeFallsIn() {
        let sut = makeSUT()
        let wholeDay = makeMealType(id: 0, hour: 0, endHour: 24)
        let source = makeMealType(id: 1, hour: 1, endHour: 2)
        sut.mealTypes = [source, wholeDay]

        sut.onCopyRequested(from: source, at: 2)

        XCTAssertEqual(sut.copyPopoverIndex, 2)
        XCTAssertTrue(Calendar.current.isDateInToday(sut.copyTargetDay))
        XCTAssertEqual(sut.copyTargetMealTypeId, "0")
    }

    func test_onCopyRequested_whenNoWindowContainsNow_defaultsToTheSourceMealType() {
        let sut = makeSUT()
        let source = makeMealTypeExcludingNow(id: 1)
        sut.mealTypes = [makeMealTypeExcludingNow(id: 0, offsetMinutes: 300), source]

        sut.onCopyRequested(from: source, at: 0)

        XCTAssertEqual(sut.copyTargetMealTypeId, "1")
    }

    func test_onCopyRequested_whenNoWindowContainsNowAndSourceIsUnassigned_defaultsToTheFirstMealType() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealTypeExcludingNow(id: 0), makeMealTypeExcludingNow(id: 1, offsetMinutes: 300)]

        sut.onCopyRequested(from: nil, at: 0)

        XCTAssertEqual(sut.copyTargetMealTypeId, "0")
    }

    // MARK: - Copy — canCopy

    func test_canCopy_whenTargetIsTheSameDayAndSameMealType_isFalse() {
        let sut = makeSUT()
        let source = makeMealType(id: 0, hour: 8, endHour: 12)
        sut.mealTypes = [source]
        sut.copyTargetDay = sut.selectedDay
        sut.copyTargetMealTypeId = "0"

        XCTAssertFalse(sut.canCopy(from: source), "copying a section onto itself would duplicate it in place, which was rejected as an accidental tap")
    }

    func test_canCopy_whenOnlyTheMealTypeDiffers_isTrue() {
        let sut = makeSUT()
        let source = makeMealType(id: 0, hour: 8, endHour: 12)
        sut.copyTargetDay = sut.selectedDay
        sut.copyTargetMealTypeId = "1"

        XCTAssertTrue(sut.canCopy(from: source))
    }

    func test_canCopy_whenOnlyTheDayDiffers_isTrue() {
        let sut = makeSUT()
        let source = makeMealType(id: 0, hour: 8, endHour: 12)
        sut.copyTargetDay = Calendar.current.date(byAdding: .day, value: -1, to: sut.selectedDay) ?? sut.selectedDay
        sut.copyTargetMealTypeId = "0"

        XCTAssertTrue(sut.canCopy(from: source))
    }

    func test_canCopy_whenSourceIsUnassigned_anyTargetCountsAsDifferent() {
        let sut = makeSUT()
        sut.copyTargetDay = sut.selectedDay
        sut.copyTargetMealTypeId = "0"

        XCTAssertTrue(sut.canCopy(from: nil))
    }

    func test_canCopy_withoutATargetMealType_isFalse() {
        let sut = makeSUT()
        sut.copyTargetMealTypeId = nil

        XCTAssertFalse(sut.canCopy(from: nil))
    }

    // MARK: - Copy — confirm

    @MainActor
    func test_onCopyConfirmed_whenCopySucceeds_reloadsTheDayAndClosesTheBox() async {
        let source = makeFood(id: "f1", hour: 8)
        let copied = makeFood(id: "f2", hour: 12)
        let sut = makeSUT(fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods: [source, copied]))
        let sourceType = makeMealType(id: 0, hour: 8, endHour: 10)
        sut.mealTypes = [sourceType, makeMealType(id: 1, hour: 11, endHour: 14)]
        sut.foodsConsumed = [source]
        sut.copyTargetDay = sut.selectedDay
        sut.copyTargetMealTypeId = "1"
        sut.copyPopoverIndex = 0

        await sut.onCopyConfirmed([source], from: sourceType)

        XCTAssertEqual(sut.foodsConsumed.map(\.id), ["f1", "f2"], "the target section must show the copy without the user refreshing")
        XCTAssertNil(sut.copyPopoverIndex)
        XCTAssertFalse(sut.showCopyCheckmark)
        XCTAssertFalse(sut.isCopying)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onCopyConfirmed_whenTargetIsAnotherMonth_keepsTheDisplayedDay() async {
        let source = makeFood(id: "f1", hour: 8)
        let sut = makeSUT(fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods: [source, makeFood(id: "f2", hour: 12)]))
        let sourceType = makeMealType(id: 0, hour: 8, endHour: 10)
        sut.mealTypes = [sourceType, makeMealType(id: 1, hour: 11, endHour: 14)]
        let displayedDay = Calendar.current.date(byAdding: .month, value: -2, to: Date.now) ?? Date.now
        sut.selectedDay = displayedDay
        sut.foodsConsumed = [source]
        sut.copyTargetDay = Date.now
        sut.copyTargetMealTypeId = "1"

        await sut.onCopyConfirmed([source], from: sourceType)

        XCTAssertEqual(sut.selectedDay, displayedDay, "a user who scrolled back must not lose their place")
        XCTAssertEqual(sut.foodsConsumed.map(\.id), ["f1"])
    }

    @MainActor
    func test_onCopyConfirmed_whenCopyFails_showsAlertAndKeepsTheBoxOpenWithoutCheckmark() async {
        let source = makeFood(id: "f1", hour: 8)
        let sut = makeSUT(copyFoodsConsumed: CopyFoodsConsumedUseCaseFake(shouldThrow: true))
        let sourceType = makeMealType(id: 0, hour: 8, endHour: 10)
        sut.mealTypes = [sourceType, makeMealType(id: 1, hour: 11, endHour: 14)]
        sut.copyTargetDay = sut.selectedDay
        sut.copyTargetMealTypeId = "1"
        sut.copyPopoverIndex = 0

        await sut.onCopyConfirmed([source], from: sourceType)

        XCTAssertNotNil(sut.alertItem)
        XCTAssertEqual(sut.copyPopoverIndex, 0)
        XCTAssertFalse(sut.showCopyCheckmark)
        XCTAssertFalse(sut.isCopying, "a stuck flag would leave Copy disabled after a failure and the user could not retry")
    }

    @MainActor
    func test_onCopyConfirmed_whenTargetIsTheSource_doesNothing() async {
        let source = makeFood(id: "f1", hour: 8)
        let sut = makeSUT(copyFoodsConsumed: CopyFoodsConsumedUseCaseFake(shouldThrow: true))
        let sourceType = makeMealType(id: 0, hour: 8, endHour: 10)
        sut.mealTypes = [sourceType]
        sut.copyTargetDay = sut.selectedDay
        sut.copyTargetMealTypeId = "0"
        sut.copyPopoverIndex = 0

        await sut.onCopyConfirmed([source], from: sourceType)

        XCTAssertNil(sut.alertItem, "the use case must not even be called when the target is the source")
        XCTAssertEqual(sut.copyPopoverIndex, 0)
    }

    // MARK: - Sign-in spotlight

    @MainActor
    func test_onAppear_whenAnonymousWithLoggedFoodAndNeverShown_showsSpotlightAndStoresNow() async {
        let store = SignInSpotlightStoreFake()
        let now = Date.now
        let sut = makeSpotlightSUT(store: store, now: now)
        await sut.onAppear()
        XCTAssertTrue(sut.isSignInSpotlightVisible, "an anonymous user's diary is lost with the device, so it must be warned")
        XCTAssertEqual(store.lastShownAt, now)
    }

    @MainActor
    func test_onAppear_whenSignedIn_neverShowsSpotlight() async {
        let store = SignInSpotlightStoreFake()
        let sut = makeSpotlightSUT(store: store, authProvider: AuthProviderFake(isAnonymous: false))
        await sut.onAppear()
        XCTAssertFalse(sut.isSignInSpotlightVisible, "a signed-in user's diary is already safe, so the prompt would be noise")
        XCTAssertNil(store.lastShownAt)
    }

    @MainActor
    func test_onAppear_whenNoFoodLogged_doesNotShowSpotlight() async {
        let sut = makeSpotlightSUT(foods: [])
        await sut.onAppear()
        XCTAssertFalse(sut.isSignInSpotlightVisible, "there is nothing to lose yet, and the spotlight on a first launch is noise")
    }

    @MainActor
    func test_onRefresh_whenShownSixDaysAgo_doesNotShowSpotlight() async {
        let now = Date.now
        let store = SignInSpotlightStoreFake(lastShownAt: now.addingTimeInterval(-6 * 24 * 60 * 60))
        let sut = makeSpotlightSUT(store: store, now: now)
        await sut.onAppear()
        await sut.onRefresh()
        XCTAssertFalse(sut.isSignInSpotlightVisible, "the spotlight repeats weekly, not more often")
    }

    @MainActor
    func test_onRefresh_whenShownSevenDaysAgo_showsSpotlight() async {
        let now = Date.now
        let store = SignInSpotlightStoreFake(lastShownAt: now.addingTimeInterval(-7 * 24 * 60 * 60))
        let sut = makeSpotlightSUT(store: store, now: now)
        await sut.onAppear()
        XCTAssertTrue(sut.isSignInSpotlightVisible, "a process that stays alive for days must still show it once the week has passed")
        XCTAssertEqual(store.lastShownAt, now)
    }

    @MainActor
    func test_onAppear_whenAnyPresentationIsActive_doesNotShowSpotlightOrBurnTheWeek() async {
        let presentations: [(name: String, present: (DashboardViewModel) -> Void)] = [
            ("settings", { $0.showSettings = true }),
            ("add food", { $0.showAddFoodSheet = true }),
            ("calendar", { $0.showCalendarSheet = true }),
            ("account", { $0.showAccountSheet = true }),
            ("alert", { $0.alertItem = AlertItem(title: "Error") }),
            ("delete confirmation", { $0.isDeleteConfirmationVisible = true }),
            ("copy popover", { $0.copyPopoverIndex = 0 }),
            ("macro popover", { $0.macroPopoverIndex = 0 })
        ]
        for presentation in presentations {
            let store = SignInSpotlightStoreFake()
            let sut = makeSpotlightSUT(store: store)
            presentation.present(sut)
            await sut.onAppear()
            XCTAssertFalse(sut.isSignInSpotlightVisible, "\(presentation.name) is up, and SwiftUI would drop a second presentation")
            XCTAssertNil(store.lastShownAt, "a dropped presentation must not burn the week (\(presentation.name))")
        }
    }

    @MainActor
    func test_onSignInSpotlightSignInTapped_hidesSpotlightAndOpensAccountSheet() async {
        let sut = makeSpotlightSUT()
        await sut.onAppear()
        sut.onSignInSpotlightSignInTapped()
        XCTAssertFalse(sut.isSignInSpotlightVisible)
        XCTAssertTrue(sut.showAccountSheet, "the bubble is a way into the existing sign-in flow, not a new one")
    }

    @MainActor
    func test_onSignInSpotlightDismissed_hidesSpotlightWithoutOpeningAccountSheetOrChangingLastShown() async {
        let store = SignInSpotlightStoreFake()
        let now = Date.now
        let sut = makeSpotlightSUT(store: store, now: now)
        await sut.onAppear()
        sut.onSignInSpotlightDismissed()
        XCTAssertFalse(sut.isSignInSpotlightVisible)
        XCTAssertFalse(sut.showAccountSheet)
        XCTAssertEqual(store.lastShownAt, now, "a dismissed spotlight still counts as shown")
    }

    @MainActor
    func test_onAppear_whenFetchFails_doesNotShowSpotlight() async {
        let sut = makeSpotlightSUT(fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(shouldThrow: true))
        await sut.onAppear()
        XCTAssertFalse(sut.isSignInSpotlightVisible, "the spotlight must not appear over an error alert")
    }

    // MARK: - Helpers

    private func makeSpotlightSUT(
        store: SignInSpotlightStoreFake = SignInSpotlightStoreFake(),
        authProvider: any AuthProviderProtocol = AuthProviderFake(),
        foods: [FoodConsumedDomain]? = nil,
        fetchFoodsConsumedForMonth: (any FetchFoodsConsumedForMonthUseCaseProtocol)? = nil,
        now: Date = .now
    ) -> DashboardViewModel {
        makeSUT(
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [makeMealType(id: 0, hour: 0, endHour: 23)]),
            fetchFoodsConsumedForMonth: fetchFoodsConsumedForMonth
                ?? FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods: foods ?? [makeFood(id: "f1", hour: 9)]),
            authProvider: authProvider,
            signInSpotlightStore: store,
            now: { now }
        )
    }

    private func makeSUT(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol = FetchFoodsConsumedForMonthUseCaseFake(),
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol = ConfirmMealTypesEmptyUseCaseFake(stubbedResult: true),
        deleteFoodConsumed: any DeleteFoodConsumedUseCaseProtocol = DeleteFoodConsumedUseCaseFake(),
        copyFoodsConsumed: any CopyFoodsConsumedUseCaseProtocol = CopyFoodsConsumedUseCaseFake(),
        authProvider: any AuthProviderProtocol = AuthProviderFake(),
        signInSpotlightStore: any SignInSpotlightStoreProtocol = SignInSpotlightStoreFake(),
        now: @escaping () -> Date = { Date.now }
    ) -> DashboardViewModel {
        let sut = DashboardViewModel(
            fetchMealTypes: fetchMealTypes,
            fetchFoodsConsumedForMonth: fetchFoodsConsumedForMonth,
            setupDefaultMeals: setupDefaultMeals,
            confirmMealTypesEmpty: confirmMealTypesEmpty,
            deleteFoodConsumed: deleteFoodConsumed,
            copyFoodsConsumed: copyFoodsConsumed,
            authProvider: authProvider,
            signInSpotlightStore: signInSpotlightStore,
            now: now
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "DashboardViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeMealType(id: Int, hour: Int, endHour: Int, minute: Int = 0) -> MealTypeDomain {
        MealTypeDomain(id: "\(id)", name: "Meal \(id)", startMinutes: hour * 60 + minute, endMinutes: endHour * 60 + minute)
    }

    private func makeMealTypeExcludingNow(id: Int, offsetMinutes: Int = 120) -> MealTypeDomain {
        let nowMinutes = Int(Date.now.minutesSinceMidnight)
        let start = (nowMinutes + offsetMinutes) % 1440
        return MealTypeDomain(id: "\(id)", name: "Meal \(id)", startMinutes: start, endMinutes: (start + 60) % 1440)
    }

    private func makeFood(id: String, hour: Int, minute: Int = 0, fiber: Double? = 1, mealTypeId: String? = nil) -> FoodConsumedDomain {
        let cal = Calendar.current
        let base = Date.now
        let date = cal.date(bySettingHour: hour, minute: minute, second: 0, of: base) ?? base
        return FoodConsumedDomain(
            id: id,
            foodItemId: id,
            foodItemKind: .catalogue,
            czName: "Jídlo",
            engName: "Food",
            weight: 100,
            date: date,
            calories: 200,
            caloriesPerHundredGrams: 200,
            energyKJ: 837,
            protein: 10,
            carbohydrate: 20,
            carbohydrateSugar: 5,
            fat: 5,
            fatSaturated: 1,
            fatUnsaturated: 2,
            fiber: fiber,
            salt: 0.2,
            mealTypeId: mealTypeId
        )
    }
}
