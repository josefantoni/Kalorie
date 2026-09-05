//
//  FoodConsumedDetailViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.08.2026.
//

import XCTest
@testable import Kalorie

final class FoodConsumedDetailViewModelTests: XCTestCase {

    // MARK: - Tests

    @MainActor
    func test_onAppear_whenCatalogueItemNoLongerResolves_disablesAddingButKeepsButtonVisible() async {
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil))

        await sut.onAppear()

        XCTAssertFalse(sut.isFavourite)
        XCTAssertFalse(sut.canToggleFavourite, "with no catalogue item to snapshot, adding must stay disabled rather than crash or write garbage")
    }

    @MainActor
    func test_onFavouriteToggled_whenAlreadyFavouriteAndCatalogueItemMissing_stillAllowsUnfavouriting() async {
        let sut = makeSUT(
            isFavouriteFood: IsFavouriteFoodUseCaseFake(stubbedResult: true),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil)
        )
        await sut.onAppear()
        XCTAssertTrue(sut.isFavourite)
        XCTAssertTrue(sut.canToggleFavourite, "removal needs only the id, so it must stay possible even when the catalogue item vanished")

        await sut.onFavouriteToggled()

        XCTAssertFalse(sut.isFavourite)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onFavouriteToggled_whenNotFavouriteAndCatalogueItemMissing_revertsAndShowsAlert() async {
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil))
        await sut.onAppear()
        XCTAssertFalse(sut.isFavourite)

        await sut.onFavouriteToggled()

        XCTAssertFalse(sut.isFavourite, "adding needs the catalogue item, so a missing snapshot must not leave the toggle stuck on")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onAppear_whenFoodItemKindIsExternalAndAlreadyFavourite_showsButtonRegardlessOfExternalLookup() async {
        let sut = makeSUT(
            food: makeFood(kind: .external),
            isFavouriteFood: IsFavouriteFoodUseCaseFake(stubbedResult: true),
            fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem: nil)
        )

        await sut.onAppear()

        XCTAssertTrue(sut.isFavourite, "an external item is favouritable via its own id, independent of the catalogue")
        XCTAssertTrue(sut.canShowFavouriteButton, "already being a favourite must show the button even when the external lookup finds nothing")
    }

    @MainActor
    func test_onAppear_whenFoodItemKindIsExternalAndNotYetFavourite_showsFavouriteButtonFromExternalLookup() async {
        let sut = makeSUT(
            food: makeFood(kind: .external),
            fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(stubbedItem: makeCatalogueItem())
        )

        await sut.onAppear()

        XCTAssertFalse(sut.isFavourite)
        XCTAssertTrue(
            sut.canShowFavouriteButton,
            "an OpenFoodFacts item is favouritable before it is favourited too; it has no Firestore catalogue entry to look up, but is resolved via OpenFoodFacts instead"
        )
    }

    @MainActor
    func test_onAppear_whenFoodItemKindIsCreatedMeal_skipsFavouriteAndCatalogueLookups() async {
        let sut = makeSUT(
            food: makeFood(kind: .createdMeal),
            isFavouriteFood: IsFavouriteFoodUseCaseFake(stubbedResult: true),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeCatalogueItem())
        )

        await sut.onAppear()

        XCTAssertFalse(sut.isFavourite, "a created meal has no catalogue counterpart to favourite, so the Dashboard never offers it")
        XCTAssertFalse(sut.canShowFavouriteButton)
    }

    // MARK: - onMealTypeSelected (staging only, no write)

    @MainActor
    func test_onMealTypeSelected_stagesTheSelectionWithoutWritingOrEnablingSaveAlone() {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 6, minute: 0), endTime: makeDate(hour: 10, minute: 0))
        let sut = makeSUT(mealTypes: [breakfast])
        XCTAssertNil(sut.mealTypeId)

        sut.onMealTypeSelected("breakfast")

        XCTAssertEqual(sut.mealTypeId, "breakfast", "the picker must reflect the pick immediately, even before Save is tapped")
        XCTAssertNil(sut.food.mealTypeId, "selecting a meal type must not write anything on its own — only onSave() persists it")
        XCTAssertTrue(sut.hasChanges, "Save must become enabled the moment a different meal type is picked")
    }

    @MainActor
    func test_onMealTypeSelected_reselectingTheAlreadyPinnedValue_doesNotEnableSave() {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 6, minute: 0), endTime: makeDate(hour: 10, minute: 0))
        let sut = makeSUT(food: makeFood(mealTypeId: "breakfast"), mealTypes: [breakfast])

        sut.onMealTypeSelected("breakfast")

        XCTAssertFalse(sut.hasChanges, "picking the value that is already pinned is not a pending change")
    }

    // MARK: - onSave — meal type pin

    @MainActor
    func test_onSave_whenOnlyMealTypeWasSelected_writesThePinWithoutTouchingWeight() async {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 6, minute: 0), endTime: makeDate(hour: 10, minute: 0))
        var didNotify = false
        let sut = makeSUT(mealTypes: [breakfast]) { didNotify = true }
        sut.onMealTypeSelected("breakfast")

        await sut.onSave()

        XCTAssertEqual(sut.food.mealTypeId, "breakfast")
        XCTAssertTrue(didNotify, "the Dashboard's cache must be invalidated so the entry moves section")
        XCTAssertNil(sut.alertItem)
        XCTAssertFalse(sut.hasChanges, "a successful save must clear the pending state")
    }

    @MainActor
    func test_onSave_whenNothingWasChanged_doesNothing() async {
        let sut = makeSUT()

        await sut.onSave()

        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onSave_whenAssignFails_leavesMealTypeIdUnchangedAndShowsAlert() async {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 6, minute: 0), endTime: makeDate(hour: 10, minute: 0))
        let sut = makeSUT(mealTypes: [breakfast], assignFoodMealType: AssignFoodMealTypeUseCaseFake(shouldThrow: true))
        sut.onMealTypeSelected("breakfast")

        await sut.onSave()

        XCTAssertNil(sut.food.mealTypeId, "a failed write must not optimistically move the entry to a section it was never saved into")
        XCTAssertNotNil(sut.alertItem)
        XCTAssertTrue(sut.hasChanges, "a failed save must leave Save enabled so the user can retry")
    }

    @MainActor
    func test_onSave_whenMealTypeWasDeletedSinceScreenOpened_refetchesAndBlocksWithAlertInsteadOfWritingADanglingId() async {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 6, minute: 0), endTime: makeDate(hour: 10, minute: 0))
        let food = makeFood(mealTypeId: "lunch")
        let sut = makeSUT(
            food: food,
            mealTypes: [breakfast],
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: [])
        )
        sut.onMealTypeSelected("breakfast")

        await sut.onSave()

        XCTAssertTrue(sut.mealTypes.isEmpty, "the screen must pick up that breakfast was deleted elsewhere instead of trusting its initial snapshot")
        XCTAssertEqual(sut.food.mealTypeId, "lunch", "an id that no longer exists must never overwrite the food's real pin")
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onSave_whenSelectionMatchesTimeResolvedButUnpinnedMealType_stillCreatesPin() async {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 0, minute: 0), endTime: makeDate(hour: 23, minute: 59))
        let food = makeFood(mealTypeId: nil)
        var didNotify = false
        let sut = makeSUT(food: food, mealTypes: [breakfast]) { didNotify = true }
        XCTAssertEqual(sut.mealTypeId, "breakfast", "the entry already displays under breakfast by time alone, before any pin exists")

        sut.onMealTypeSelected("breakfast")
        await sut.onSave()

        XCTAssertEqual(sut.food.mealTypeId, "breakfast", "confirming the meal type the entry already resolves to by time must still create an explicit pin")
        XCTAssertTrue(didNotify)
    }

    @MainActor
    func test_onSave_whenBothWeightAndMealTypeChanged_writesBoth() async {
        let breakfast = MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: makeDate(hour: 6, minute: 0), endTime: makeDate(hour: 10, minute: 0))
        let sut = makeSUT(mealTypes: [breakfast])
        sut.weight = 150
        sut.onMealTypeSelected("breakfast")

        await sut.onSave()

        XCTAssertEqual(sut.food.weight, 150)
        XCTAssertEqual(sut.food.mealTypeId, "breakfast")
        XCTAssertFalse(sut.hasChanges)
        XCTAssertNil(sut.alertItem)
    }

    // MARK: - Helpers

    private func makeCatalogueItem() -> FoodItemDomain {
        FoodItemDomain(
            id: "12345",
            kind: .catalogue,
            czName: "Ovesné vločky",
            engName: "Oats",
            weight: 80,
            date: .now,
            energyKJ: 1500,
            caloriesPerHundredGrams: 370,
            fat: 7,
            fatSaturated: 1,
            fatUnsaturatedFattyAcids: 6,
            carbohydrate: 65,
            carbohydratePureSugar: 1,
            fiber: 10,
            protein: 13,
            salt: 0
        )
    }

    private func makeSUT(
        food: FoodConsumedDomain? = nil,
        mealTypes: [MealTypeDomain] = [],
        updateFoodConsumed: any UpdateFoodConsumedUseCaseProtocol = UpdateFoodConsumedUseCaseFake(),
        assignFoodMealType: any AssignFoodMealTypeUseCaseProtocol = AssignFoodMealTypeUseCaseFake(),
        fetchMealTypes: (any FetchMealTypesUseCaseProtocol)? = nil,
        isFavouriteFood: any IsFavouriteFoodUseCaseProtocol = IsFavouriteFoodUseCaseFake(),
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        fetchFoodByBarcodeExternally: any FetchFoodByBarcodeExternallyUseCaseProtocol = FetchFoodByBarcodeExternallyUseCaseFake(),
        onFoodUpdated: @escaping () -> Void = {}
    ) -> FoodConsumedDetailViewModel {
        let sut = FoodConsumedDetailViewModel(
            food: food ?? makeFood(),
            mealTypes: mealTypes,
            updateFoodConsumed: updateFoodConsumed,
            assignFoodMealType: assignFoodMealType,
            fetchMealTypes: fetchMealTypes ?? FetchMealTypesUseCaseFake(stubbedTypes: mealTypes),
            isFavouriteFood: isFavouriteFood,
            addFavouriteFood: addFavouriteFood,
            removeFavouriteFood: removeFavouriteFood,
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            fetchFoodByBarcodeExternally: fetchFoodByBarcodeExternally,
            onFoodUpdated: onFoodUpdated
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "FoodConsumedDetailViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeDate(hour: Int, minute: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }

    private func makeFood(foodItemId: String = "12345", kind: FoodItemKind = .catalogue, mealTypeId: String? = nil) -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: "1",
            foodItemId: foodItemId,
            foodItemKind: kind,
            czName: "Ovesné vločky",
            engName: "Oats",
            weight: 80,
            date: .now,
            calories: 295,
            caloriesPerHundredGrams: 368.75,
            energyKJ: 1544,
            protein: 10,
            carbohydrate: 52,
            carbohydrateSugar: 8,
            fat: 5,
            fatSaturated: 1,
            fatUnsaturated: 2,
            fiber: 6,
            salt: 0.1,
            mealTypeId: mealTypeId
        )
    }
}
