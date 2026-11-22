//
//  DashboardViewModelReviewPromptTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 22.11.2026.
//

import Combine
import XCTest
@testable import Kalorie

final class DashboardViewModelReviewPromptTests: XCTestCase {

    private static let day: TimeInterval = 24 * 60 * 60

    @MainActor
    func test_onFoodAdded_whenTheThirdLoggingDayBegins_showsTheCardRightAway() async {
        let clock = Clock(start: Self.date(day: 10))
        let store = ReviewPromptStoreFake()
        let sut = makeSUT(store: store, clock: clock)
        await sut.onAppear()

        await addFood(sut)
        clock.advance(by: Self.day)
        await addFood(sut)
        XCTAssertFalse(sut.isReviewCardVisible, "the ask comes only after real use, not on the second day")

        clock.advance(by: Self.day)
        await addFood(sut)
        XCTAssertTrue(sut.isReviewCardVisible, "on the third day the card must appear right after the first save, not on the next launch")
    }

    @MainActor
    func test_onFoodAdded_whenSeveralFoodsAreAddedOnOneDay_countsOneLoggingDay() async {
        let store = ReviewPromptStoreFake()
        let sut = makeSUT(store: store, clock: Clock(start: Self.date(day: 10)))
        await sut.onAppear()

        await addFood(sut)
        await addFood(sut)
        await addFood(sut)
        XCTAssertEqual(store.loggingDays.count, 1, "the rule measures days of use, not entries")
        XCTAssertFalse(sut.isReviewCardVisible)
    }

    @MainActor
    func test_onFoodAdded_whenAPastDayIsSelected_recordsTodayNotTheSelectedDay() async {
        let clock = Clock(start: Self.date(day: 10))
        let store = ReviewPromptStoreFake()
        let sut = makeSUT(store: store, clock: clock)
        await sut.onAppear()

        await sut.onDaySelected(Date.now.addingTimeInterval(-5 * Self.day))
        await addFood(sut)
        XCTAssertEqual(store.loggingDays, [BodyWeightDayKey.make(from: clock.now)], "backfilling is not three days of use")
    }

    @MainActor
    func test_onFoodConsumedUpdated_doesNotRecordALoggingDay() async {
        let store = ReviewPromptStoreFake()
        let sut = makeSUT(store: store, clock: Clock(start: Self.date(day: 10)))
        await sut.onAppear()

        await sut.onFoodConsumedUpdated()
        XCTAssertTrue(store.loggingDays.isEmpty, "editing an existing entry is not logging a day")
    }

    @MainActor
    func test_onReviewNotNowTapped_bringsTheCardBackAtThreeFiveEightThirteenThenEveryTwentyOneDays() async {
        let clock = Clock(start: Self.date(day: 10))
        let sut = makeSUT(store: dueStore(), clock: clock)
        await sut.onAppear()
        XCTAssertTrue(sut.isReviewCardVisible)

        for days in [3, 5, 8, 13, 21, 21, 21] {
            sut.onReviewNotNowTapped()
            XCTAssertFalse(sut.isReviewCardVisible)

            clock.advance(by: TimeInterval(days) * Self.day - 1)
            await sut.onRefresh()
            XCTAssertFalse(sut.isReviewCardVisible, "one second before \(days) days the card must still be snoozed")

            clock.advance(by: 1)
            await sut.onRefresh()
            XCTAssertTrue(sut.isReviewCardVisible, "after \(days) days the card must return")
        }
    }

    @MainActor
    func test_onReviewNotNowTapped_whenTappedTwice_countsOneDismissal() async {
        let store = dueStore()
        let sut = makeSUT(store: store, clock: Clock(start: Self.date(day: 10)))
        await sut.onAppear()

        sut.onReviewNotNowTapped()
        sut.onReviewNotNowTapped()

        XCTAssertEqual(store.dismissCount, 1, "a double tap must not skip a step of the 3/5/8/13 schedule")
    }

    @MainActor
    func test_onReviewRateTapped_hidesTheCardForGood() async {
        let clock = Clock(start: Self.date(day: 10))
        let store = dueStore()
        let sut = makeSUT(store: store, clock: clock)
        await sut.onAppear()

        sut.onReviewRateTapped()
        XCTAssertFalse(sut.isReviewCardVisible)
        XCTAssertTrue(store.isCompleted)

        store.dismissCount = 1
        store.lastDismissedAt = clock.now.addingTimeInterval(-30 * Self.day)
        await sut.onRefresh()
        XCTAssertFalse(sut.isReviewCardVisible, "a user who rated is never asked again")
    }

    @MainActor
    func test_onAppear_whenTheSpotlightWasShownToday_hidesTheCardUntilTomorrow() async {
        let clock = Clock(start: Self.date(day: 10))
        let spotlightStore = SignInSpotlightStoreFake(lastShownAt: clock.now.addingTimeInterval(-60))
        let sut = makeSUT(store: dueStore(), clock: clock, spotlightStore: spotlightStore)
        await sut.onAppear()
        XCTAssertFalse(sut.isReviewCardVisible, "one ask per day, and the diary-safety ask wins")

        clock.advance(by: Self.day)
        await sut.onRefresh()
        XCTAssertTrue(sut.isReviewCardVisible)
    }

    @MainActor
    func test_onAppear_whenTheSelectedDayIsEmpty_doesNotShowTheCard() async {
        let sut = makeSUT(store: dueStore(), clock: Clock(start: Self.date(day: 10)), foods: [])
        await sut.onAppear()
        XCTAssertFalse(sut.isReviewCardVisible, "the card sits under the day summary, and an empty day shows the empty state")
    }

    @MainActor
    func test_onAppear_whenTheSpotlightFiresInTheSamePass_neverShowsTheCard() async {
        let clock = Clock(start: Self.date(day: 10))
        let spotlightStore = SignInSpotlightStoreFake(lastShownAt: clock.now.addingTimeInterval(-8 * Self.day))
        let sut = makeSUT(store: dueStore(), clock: clock, spotlightStore: spotlightStore, isAnonymous: true)
        var values: [Bool] = []
        let cancellable = sut.$isReviewCardVisible.sink { values.append($0) }

        await sut.onAppear()

        XCTAssertTrue(sut.isSignInSpotlightVisible)
        XCTAssertFalse(values.contains(true), "the spotlight wins the pass, so the card must never flash first: \(values)")
        cancellable.cancel()
    }

    @MainActor
    func test_onFoodAdded_whenTheAddFoodSheetIsStillUp_doesNotShowTheCardBehindIt() async {
        let clock = Clock(start: Self.date(day: 10))
        let spotlightStore = SignInSpotlightStoreFake(lastShownAt: clock.now.addingTimeInterval(-8 * Self.day))
        let sut = makeSUT(store: dueStore(), clock: clock, spotlightStore: spotlightStore, isAnonymous: true)
        sut.showAddFoodSheet = true
        await sut.onAppear()

        await sut.onFoodAdded()
        XCTAssertFalse(sut.isReviewCardVisible)

        sut.showAddFoodSheet = false
        sut.onAddFoodSheetDismissed()
        XCTAssertTrue(sut.isSignInSpotlightVisible)
        XCTAssertFalse(sut.isReviewCardVisible, "the spotlight that fires after the sheet wins the day")
    }

    // MARK: - Helpers

    private final class Clock {
        var now: Date
        init(start: Date) { now = start }
        func advance(by interval: TimeInterval) { now = now.addingTimeInterval(interval) }
    }

    private static func date(day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: day, hour: 12)) ?? .now
    }

    @MainActor
    private func addFood(_ sut: DashboardViewModel) async {
        await sut.onFoodAdded()
    }

    private func dueStore(dismissCount: Int = 0, lastDismissedAt: Date? = nil) -> ReviewPromptStoreFake {
        ReviewPromptStoreFake(
            loggingDays: ["2026-11-01", "2026-11-02", "2026-11-03"],
            dismissCount: dismissCount,
            lastDismissedAt: lastDismissedAt
        )
    }

    private func makeSUT(
        store: ReviewPromptStoreFake,
        clock: Clock,
        spotlightStore: SignInSpotlightStoreFake = SignInSpotlightStoreFake(),
        foods: [FoodConsumedDomain]? = nil,
        isAnonymous: Bool = false
    ) -> DashboardViewModel {
        let sut = DashboardViewModel(
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [MealTypeDomain(id: "0", name: "Meal", startMinutes: 0, endMinutes: 23 * 60)]),
            fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseFake(stubbedFoods: foods ?? [makeFood()]),
            fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseFake(stubbedFoods: foods ?? [makeFood()]),
            setupDefaultMeals: SetupDefaultMealsUseCaseFake(),
            confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseFake(stubbedResult: true),
            deleteFoodConsumed: DeleteFoodConsumedUseCaseFake(),
            copyFoodsConsumed: CopyFoodsConsumedUseCaseFake(),
            assignFoodMealType: AssignFoodMealTypeUseCaseFake(),
            fetchBodyWeights: FetchBodyWeightsUseCaseFake(),
            fetchAppSettings: FetchAppSettingsUseCaseFake(),
            fetchDailyGoals: FetchDailyGoalsUseCaseFake(),
            saveBodyWeight: SaveBodyWeightUseCaseFake(),
            authProvider: AuthProviderFake(isAnonymous: isAnonymous),
            signInSpotlightStore: spotlightStore,
            reviewPromptStore: store
        ) {
            clock.now
        }
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "DashboardViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeFood() -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: "f1",
            foodItemId: "f1",
            foodItemKind: .catalogue,
            czName: "Jídlo",
            skName: "Jídlo",
            engName: "Food",
            weight: 100,
            date: .now,
            calories: 200,
            caloriesPerHundredGrams: 200,
            energyKJ: 837,
            protein: 10,
            carbohydrate: 20,
            carbohydrateSugar: 5,
            fat: 5,
            fatSaturated: 1,
            fatUnsaturated: 2,
            fiber: 1,
            salt: 0.2,
            mealTypeId: nil
        )
    }
}
