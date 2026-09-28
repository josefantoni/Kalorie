//
//  SettingsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class SettingsViewModelTests: XCTestCase {

    // MARK: - onMove

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

    // MARK: - onShowAddForm

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

    // MARK: - onAppear

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

    // MARK: - Helpers

    private func makeSUT(
        mealTypes: [MealTypeDomain] = [],
        createMealType: any CreateMealTypeUseCaseProtocol = CreateMealTypeUseCaseFake(),
        deleteMealType: any DeleteMealTypeUseCaseProtocol = DeleteMealTypeUseCaseFake(),
        updateMealTypeTimes: any UpdateMealTypeTimesUseCaseProtocol = UpdateMealTypeTimesUseCaseFake(),
        fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol = FetchMaintainerClaimUseCaseFake()
    ) -> SettingsViewModel {
        SettingsViewModel(
            mealTypes: mealTypes,
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
