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

    // MARK: - Helpers

    private func makeSUT(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol = FetchFoodsConsumedForMonthUseCaseFake(),
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol = ConfirmMealTypesEmptyUseCaseFake(stubbedResult: true),
        deleteFoodConsumed: any DeleteFoodConsumedUseCaseProtocol = DeleteFoodConsumedUseCaseFake()
    ) -> DashboardViewModel {
        let sut = DashboardViewModel(
            fetchMealTypes: fetchMealTypes,
            fetchFoodsConsumedForMonth: fetchFoodsConsumedForMonth,
            setupDefaultMeals: setupDefaultMeals,
            confirmMealTypesEmpty: confirmMealTypesEmpty,
            deleteFoodConsumed: deleteFoodConsumed
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "DashboardViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeMealType(id: Int, hour: Int, endHour: Int, minute: Int = 0) -> MealTypeDomain {
        let cal = Calendar.current
        let base = Date.now
        let start = cal.date(bySettingHour: hour, minute: minute, second: 0, of: base) ?? base
        let end = cal.date(bySettingHour: endHour, minute: minute, second: 0, of: base) ?? base
        return MealTypeDomain(id: "\(id)", name: "Meal \(id)", startTime: start, endTime: end)
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
