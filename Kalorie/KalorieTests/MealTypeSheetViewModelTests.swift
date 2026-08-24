//
//  MealTypeSheetViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class MealTypeSheetViewModelTests: XCTestCase {

    // MARK: - onMove

    @MainActor
    func test_onMove_movingFirstItemToLast_keepsTimeSlotsAtPositions() {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let meal2 = makeMealType(id: 2, name: "C", hour: 16, endHour: 20)
        let sut = makeSUT(mealTypes: [meal0, meal1, meal2])

        sut.onMove(from: IndexSet(integer: 0), to: 3)

        XCTAssertEqual(sut.mealTypes[0].name, "B")
        XCTAssertEqual(sut.mealTypes[0].startTime, meal0.startTime)
        XCTAssertEqual(sut.mealTypes[1].name, "C")
        XCTAssertEqual(sut.mealTypes[1].startTime, meal1.startTime)
        XCTAssertEqual(sut.mealTypes[2].name, "A")
        XCTAssertEqual(sut.mealTypes[2].startTime, meal2.startTime)
    }

    @MainActor
    func test_onMove_movingLastItemToFirst_keepsTimeSlotsAtPositions() {
        let meal0 = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let meal1 = makeMealType(id: 1, name: "B", hour: 12, endHour: 16)
        let meal2 = makeMealType(id: 2, name: "C", hour: 16, endHour: 20)
        let sut = makeSUT(mealTypes: [meal0, meal1, meal2])

        sut.onMove(from: IndexSet(integer: 2), to: 0)

        XCTAssertEqual(sut.mealTypes[0].name, "C")
        XCTAssertEqual(sut.mealTypes[0].startTime, meal0.startTime)
        XCTAssertEqual(sut.mealTypes[1].name, "A")
        XCTAssertEqual(sut.mealTypes[1].startTime, meal1.startTime)
        XCTAssertEqual(sut.mealTypes[2].name, "B")
        XCTAssertEqual(sut.mealTypes[2].startTime, meal2.startTime)
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

    // MARK: - onSaveReorder

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

    // MARK: - onDelete

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
        XCTAssertEqual(sut.mealTypes[0].id, 1)
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

    // MARK: - onShowAddForm

    func test_onShowAddForm_withExistingMealTypes_setsStartAfterLastEnd() {
        let meal = makeMealType(id: 0, name: "A", hour: 8, endHour: 12)
        let sut = makeSUT(mealTypes: [meal])
        sut.onShowAddForm()
        XCTAssertTrue(sut.isAddFormVisible)
        XCTAssertEqual(sut.newMealStart, meal.endTime)
    }

    func test_onShowAddForm_withNoMealTypes_makesFormVisible() {
        let sut = makeSUT(mealTypes: [])
        sut.onShowAddForm()
        XCTAssertTrue(sut.isAddFormVisible)
    }

    // MARK: - Helpers

    private func makeSUT(
        mealTypes: [MealTypeDomain] = [],
        createMealType: any CreateMealTypeUseCaseProtocol = CreateMealTypeUseCaseFake(),
        deleteMealType: any DeleteMealTypeUseCaseProtocol = DeleteMealTypeUseCaseFake(),
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol = UpdateMealTypeTimesUseCaseFake()
    ) -> MealTypeSheetViewModel {
        MealTypeSheetViewModel(
            mealTypes: mealTypes,
            createMealType: createMealType,
            deleteMealType: deleteMealType,
            updateMealTypeTimes: updateMealTypeTimes
        )
    }

    private func makeMealType(id: Int, name: String, hour: Int, endHour: Int) -> MealTypeDomain {
        let cal = Calendar.current
        let base = Date.now
        let start = cal.date(bySettingHour: hour, minute: 0, second: 0, of: base) ?? base
        let end = cal.date(bySettingHour: endHour, minute: 0, second: 0, of: base) ?? base
        return MealTypeDomain(id: id, name: name, startTime: start, endTime: end)
    }
}
