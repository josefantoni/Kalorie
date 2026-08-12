//
//  DashboardViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class DashboardViewModelTests: XCTestCase {

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
        XCTAssertEqual(groups[0].mealType?.id, 0)
        XCTAssertEqual(groups[0].foods.first?.id, "f1")
    }

    func test_groupedFoods_foodAtExactStartTime_isIncluded() {
        let sut = makeSUT()
        sut.mealTypes = [makeMealType(id: 0, hour: 8, endHour: 12)]
        sut.foodsConsumed = [makeFood(id: "f1", hour: 8, minute: 0)]
        let groups = sut.groupedFoods
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].mealType?.id, 0)
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
        XCTAssertEqual(groups[0].mealType?.id, 0)
        XCTAssertEqual(groups[1].mealType?.id, 1)
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
        XCTAssertEqual(groups[0].mealType?.id, 0)
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

    // MARK: - Helpers

    private func makeSUT(
        fetchMealTypes: any FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        fetchFoodsConsumedForMonth: any FetchFoodsConsumedForMonthUseCaseProtocol = FetchFoodsConsumedForMonthUseCaseFake(),
        setupDefaultMeals: any SetupDefaultMealsUseCaseProtocol = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty: any ConfirmMealTypesEmptyUseCaseProtocol = ConfirmMealTypesEmptyUseCaseFake(stubbedResult: true)
    ) -> DashboardViewModel {
        let sut = DashboardViewModel(
            fetchMealTypes: fetchMealTypes,
            fetchFoodsConsumedForMonth: fetchFoodsConsumedForMonth,
            setupDefaultMeals: setupDefaultMeals,
            confirmMealTypesEmpty: confirmMealTypesEmpty
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
        return MealTypeDomain(id: id, name: "Meal \(id)", startTime: start, endTime: end)
    }

    private func makeFood(id: String, hour: Int, minute: Int = 0) -> FoodConsumedDomain {
        let cal = Calendar.current
        let base = Date.now
        let date = cal.date(bySettingHour: hour, minute: minute, second: 0, of: base) ?? base
        return FoodConsumedDomain(
            id: id,
            foodItemId: id,
            czName: "Jídlo",
            engName: "Food",
            weight: 100,
            date: date,
            calories: 200,
            protein: 10,
            carbohydrate: 20,
            carbohydrateSugar: 5,
            fat: 5,
            fatUnsaturated: 2,
            fiber: 1,
            salt: 0.2
        )
    }
}
