//
//  FoodQuantityViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.07.2026.
//

import XCTest
@testable import Kalorie

final class FoodQuantityViewModelTests: XCTestCase {

    // MARK: - grams

    func test_grams_withOneHundredGramUnit_is100() {
        let sut = makeSUT()
        sut.unit = .hundredGrams
        sut.quantity = 1
        XCTAssertEqual(sut.grams, 100)
    }

    func test_grams_withTwoHundredGrams_is200() {
        let sut = makeSUT()
        sut.unit = .hundredGrams
        sut.quantity = 2
        XCTAssertEqual(sut.grams, 200)
    }

    func test_grams_withGramsUnit_equalsQuantity() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 150
        XCTAssertEqual(sut.grams, 150)
    }

    // MARK: - scaledCalories

    func test_scaledCalories_calculatesFromGrams() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 200))
        sut.unit = .grams
        sut.quantity = 250
        XCTAssertEqual(sut.scaledCalories, 500)
    }

    func test_scaledCalories_withOneHundredGramOf200kcalItem_is200() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 200))
        sut.unit = .hundredGrams
        sut.quantity = 1
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    func test_savesRoundedCalories_whenScalingProducesFraction() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 133))
        sut.unit = .grams
        sut.quantity = 150
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    func test_scaledCalories_whenCaloriesPerHundredGramsIsFractional_roundsOnce() {
        let sut = makeSUT(item: makeFoodItem(caloriesPerHundredGrams: 133.6))
        sut.unit = .grams
        sut.quantity = 150
        XCTAssertEqual(sut.scaledCalories, 200)
    }

    // MARK: - scaledFiber

    func test_scaledFiber_whenItemsFiberIsUnknown_showsZeroInsteadOfNil() {
        let sut = makeSUT(item: makeFoodItem(fiber: nil))
        XCTAssertEqual(sut.scaledFiber, 0)
    }

    // MARK: - init defaults

    func test_init_withoutQuantityOrUnit_defaultsToOneHundredGram() {
        let sut = makeSUT()
        XCTAssertEqual(sut.quantity, 1)
        XCTAssertEqual(sut.unit, .hundredGrams)
    }

    func test_init_withExplicitQuantityAndUnit_usesThemInsteadOfTheDefault() {
        let sut = makeSUT(quantity: 340, unit: .grams)
        XCTAssertEqual(sut.quantity, 340)
        XCTAssertEqual(sut.unit, .grams)
    }

    // MARK: - onUnitSelected

    func test_onUnitSelected_toGrams_convertsQuantity() {
        let sut = makeSUT()
        sut.unit = .hundredGrams
        sut.quantity = 1
        sut.onUnitSelected(.grams)
        XCTAssertEqual(sut.quantity, 100)
    }

    func test_onUnitSelected_toHundredGrams_convertsQuantity() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 200
        sut.onUnitSelected(.hundredGrams)
        XCTAssertEqual(sut.quantity, 2)
    }

    func test_onUnitSelected_toHundredGrams_keepsFraction() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 150
        sut.onUnitSelected(.hundredGrams)
        XCTAssertEqual(sut.quantity, 1.5)
    }

    func test_onUnitSelected_toHundredGrams_belowFiftyGrams_doesNotFloorToOne() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 20
        sut.onUnitSelected(.hundredGrams)
        XCTAssertEqual(sut.quantity, 0.2)
    }

    func test_onUnitSelected_toHundredGramsAndBack_roundTripsExactly() {
        let sut = makeSUT()
        sut.unit = .grams
        sut.quantity = 150
        sut.onUnitSelected(.hundredGrams)
        sut.onUnitSelected(.grams)
        XCTAssertEqual(sut.quantity, 150)
    }

    func test_onUnitSelected_setsUnit() {
        let sut = makeSUT()
        let portion = FoodPortionDomain(name: "1 balení", grams: 250)
        sut.onUnitSelected(.portion(portion))
        XCTAssertEqual(sut.unit, .portion(portion))
    }

    @MainActor
    func test_onUnitSelected_thenOnAppearResolvesPersonalPortions_doesNotOverrideUserChoice() async {
        let cataloguePortion = FoodPortionDomain(name: "1 balení", grams: 250)
        let personalPortion = FoodPortionDomain(name: "1 hrnek", grams: 40)
        let sut = makeSUT(
            item: makeFoodItem(portions: [cataloguePortion]),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [personalPortion])
        )
        sut.onUnitSelected(.grams)
        await sut.onAppear()
        XCTAssertEqual(
            sut.unit,
            .grams,
            "a personal portion resolving after the user already picked a unit must not override that choice"
        )
    }

    // MARK: - onConfirm

    @MainActor
    func test_onConfirm_withZeroQuantity_showsInvalidQuantityAlert() async {
        let sut = makeSUT()
        sut.quantity = 0
        await sut.onConfirm()
        XCTAssertEqual(sut.alertItem?.title, L10n.FoodQuantity.errorInvalidQuantity)
    }

    @MainActor
    func test_onConfirm_whenSaveSucceeds_callsOnSaved() async {
        var onSavedCalled = false
        let sut = makeSUT { onSavedCalled = true }
        await sut.onConfirm()
        XCTAssertTrue(onSavedCalled)
    }

    @MainActor
    func test_onConfirm_whenSaveSucceeds_setsLoadedState() async {
        let sut = makeSUT()
        await sut.onConfirm()
        XCTAssertFalse(sut.state.isLoading)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onConfirm_whenSaveFails_showsAlert() async {
        let sut = makeSUT(saveFoodConsumed: SaveFoodConsumedUseCaseFake(shouldThrow: true))
        await sut.onConfirm()
        XCTAssertNotNil(sut.alertItem)
    }

    @MainActor
    func test_onConfirm_whenSaveFails_doesNotCallOnSaved() async {
        var onSavedCalled = false
        let sut = makeSUT(saveFoodConsumed: SaveFoodConsumedUseCaseFake(shouldThrow: true)) {
            onSavedCalled = true
        }
        await sut.onConfirm()
        XCTAssertFalse(onSavedCalled)
    }

    @MainActor
    func test_onConfirm_usesFreshlyFetchedMealTypesInsteadOfStaleSnapshot() async {
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 12, minute: 0, second: 0, of: .now) ?? .now
        let staleMealTypes = [makeMealType(id: "breakfast", hour: 6, endHour: 10)]
        let freshMealTypes = [makeMealType(id: "lunch", hour: 11, endHour: 14)]
        let spy = SaveFoodConsumedUseCaseSpy()
        let sut = makeSUT(
            saveFoodConsumed: spy,
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: freshMealTypes),
            selectedDate: loggedAt,
            mealTypes: staleMealTypes
        )
        await sut.onConfirm()
        XCTAssertEqual(
            spy.capturedMealTypeId,
            "lunch",
            "onConfirm must resolve the meal-type pin against meal types fetched at save time, not the array captured when the sheet was opened"
        )
    }

    @MainActor
    func test_onConfirm_whenMealTypesRefetchFails_fallsBackToOriginalSnapshot() async {
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 8, minute: 0, second: 0, of: .now) ?? .now
        let originalMealTypes = [makeMealType(id: "breakfast", hour: 6, endHour: 10)]
        let spy = SaveFoodConsumedUseCaseSpy()
        let sut = makeSUT(
            saveFoodConsumed: spy,
            fetchMealTypes: FetchMealTypesUseCaseFake(shouldThrow: true),
            selectedDate: loggedAt,
            mealTypes: originalMealTypes
        )
        await sut.onConfirm()
        XCTAssertEqual(spy.capturedMealTypeId, "breakfast", "a failed refetch must fall back to the snapshot captured when the sheet was opened, not discard it")
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onConfirm_whenUserPickedAMealType_usesThatInsteadOfTheTimeBasedDefault() async {
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 12, minute: 0, second: 0, of: .now) ?? .now
        let mealTypes = [makeMealType(id: "lunch", hour: 11, endHour: 14), makeMealType(id: "dinner", hour: 18, endHour: 21)]
        let spy = SaveFoodConsumedUseCaseSpy()
        let sut = makeSUT(
            saveFoodConsumed: spy,
            fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: mealTypes),
            selectedDate: loggedAt,
            mealTypes: mealTypes
        )
        sut.onMealTypeSelected("dinner")
        await sut.onConfirm()
        XCTAssertEqual(spy.capturedMealTypeId, "dinner", "an explicit pick overrides the time-of-day default, mirroring the edit screen's picker")
    }

    @MainActor
    func test_onConfirm_whenPickedMealTypeNoLongerExistsAfterRefetch_showsAlertAndDoesNotSave() async {
        let mealTypes = [makeMealType(id: "lunch", hour: 11, endHour: 14)]
        let spy = SaveFoodConsumedUseCaseSpy()
        let sut = makeSUT(saveFoodConsumed: spy, fetchMealTypes: FetchMealTypesUseCaseFake(stubbedTypes: []), mealTypes: mealTypes)
        sut.onMealTypeSelected("lunch")
        await sut.onConfirm()
        XCTAssertFalse(spy.wasCalled, "a pin to a meal type deleted since the sheet opened must not be silently written")
        XCTAssertEqual(sut.alertItem?.title, L10n.Common.errorUnknown)
    }

    // MARK: - selectedMealTypeId (init)

    func test_init_preselectsTheMealTypeResolvedFromTimeOfDay() {
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 12, minute: 0, second: 0, of: .now) ?? .now
        let sut = makeSUT(selectedDate: loggedAt, mealTypes: [makeMealType(id: "lunch", hour: 11, endHour: 14)])
        XCTAssertEqual(sut.selectedMealTypeId, "lunch")
    }

    func test_init_whenNoWindowMatches_preselectsNoMealType() {
        let cal = Calendar.current
        let loggedAt = cal.date(bySettingHour: 3, minute: 0, second: 0, of: .now) ?? .now
        let sut = makeSUT(selectedDate: loggedAt, mealTypes: [makeMealType(id: "breakfast", hour: 6, endHour: 10)])
        XCTAssertNil(sut.selectedMealTypeId)
    }

    // MARK: - unitOptions

    func test_unitOptions_ordersCataloguePortionBeforeGramsBeforeHundredGrams() {
        let slice = FoodPortionDomain(name: "1 plátek", grams: 30)
        let sut = makeSUT(item: makeFoodItem(portions: [slice]))
        XCTAssertEqual(sut.unitOptions, [.portion(slice), .grams, .hundredGrams])
    }

    @MainActor
    func test_unitOptions_ordersPersonalPortionsBeforeCataloguePortionsAndGenericUnits() async {
        let cataloguePortion = FoodPortionDomain(name: "1 balení", grams: 250)
        let personalPortion = FoodPortionDomain(name: "1 hrnek", grams: 40)
        let sut = makeSUT(
            item: makeFoodItem(portions: [cataloguePortion]),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [personalPortion])
        )
        await sut.onAppear()
        XCTAssertEqual(
            sut.unitOptions,
            [.portion(personalPortion), .portion(cataloguePortion), .grams, .hundredGrams],
            "ADR 0030: the user's own shortcuts lead the list, ahead of the item's canonical portions"
        )
    }

    // MARK: - defaultUnit(for:)

    func test_defaultUnit_withCataloguePortions_returnsFirstPortion() {
        let firstPortion = FoodPortionDomain(name: "1 balení", grams: 80)
        let secondPortion = FoodPortionDomain(name: "1 plátek", grams: 30)
        let item = makeFoodItem(portions: [firstPortion, secondPortion])
        XCTAssertEqual(
            FoodQuantityViewModel.defaultUnit(for: item),
            .portion(firstPortion),
            "a catalogue-defined portion is the fastest way to log a packaged food, so it must be pre-selected"
        )
    }

    func test_defaultUnit_withoutCataloguePortions_returnsGrams() {
        let item = makeFoodItem(portions: [])
        XCTAssertEqual(FoodQuantityViewModel.defaultUnit(for: item), .grams)
    }

    // MARK: - onAppear (personal portions)

    @MainActor
    func test_onAppear_withCatalogueItem_fetchesPersonalPortions() async {
        let stubbedPortion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(
            item: makeFoodItem(kind: .catalogue),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [stubbedPortion])
        )
        await sut.onAppear()
        XCTAssertEqual(sut.personalPortions, [stubbedPortion])
    }

    @MainActor
    func test_onAppear_withPersonalPortions_movesDefaultToFirstPersonalPortion() async {
        let cataloguePortion = FoodPortionDomain(name: "1 balení", grams: 250)
        let personalPortion = FoodPortionDomain(name: "1 hrnek", grams: 40)
        let sut = makeSUT(
            item: makeFoodItem(portions: [cataloguePortion]),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [personalPortion]),
            unit: FoodQuantityViewModel.defaultUnit(for: makeFoodItem(portions: [cataloguePortion]))
        )
        await sut.onAppear()
        XCTAssertEqual(
            sut.unit,
            .portion(personalPortion),
            "ADR 0030: the first option in the list is always the preselected unit, so once personal portions resolve the default follows them"
        )
        XCTAssertEqual(sut.quantity, 1, "the step-2 reselection must not be treated as a unit change and rescale the quantity")
    }

    @MainActor
    func test_onAppear_withPersonalPortionsAndNoCataloguePortion_resetsQuantityFromTheGramsFallbackDefault() async {
        let personalPortion = FoodPortionDomain(name: "1 hrnek", grams: 40)
        let sut = makeSUT(
            item: makeFoodItem(portions: []),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [personalPortion]),
            quantity: 100,
            unit: .grams
        )
        await sut.onAppear()
        XCTAssertEqual(
            sut.quantity,
            1,
            "a portion resolving into the plain-grams default (quantity 100, ADR 0030) must reset to 1, " +
                "or the screen would open at 100 × 1 hrnek instead of 1 × 1 hrnek"
        )
    }

    @MainActor
    func test_onAppear_withoutPersonalPortions_keepsSynchronousDefault() async {
        let cataloguePortion = FoodPortionDomain(name: "1 balení", grams: 250)
        let sut = makeSUT(
            item: makeFoodItem(portions: [cataloguePortion]),
            unit: .portion(cataloguePortion)
        )
        await sut.onAppear()
        XCTAssertEqual(sut.unit, .portion(cataloguePortion))
    }

    @MainActor
    func test_onAppear_withExternalItem_doesNotSurfacePersonalPortions() async {
        let stubbedPortion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(
            item: makeFoodItem(kind: .external),
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [stubbedPortion])
        )
        await sut.onAppear()
        XCTAssertTrue(
            sut.personalPortions.isEmpty,
            "OpenFoodFacts items have no reliable package size to key a personal portion off (design 0008)"
        )
    }

    // MARK: - Reporting incorrect data

    func test_canReportIncorrectData_onlyTrueForCatalogueKind() {
        XCTAssertTrue(makeSUT(item: makeFoodItem(kind: .catalogue)).canReportIncorrectData)
        XCTAssertFalse(makeSUT(item: makeFoodItem(kind: .external)).canReportIncorrectData, "an OpenFoodFacts item is not ours to correct")
    }

    @MainActor
    func test_onAppear_whenAlreadyReported_setsHasReportedCurrentItem() async {
        let sut = makeSUT(
            item: makeFoodItem(kind: .catalogue),
            fetchMyFoodItemReport: FetchMyFoodItemReportUseCaseFake(
                stubbedReport: FoodItemReportDomain(barcode: "test", reportedBy: "test-user-id", reason: "wrong", reportedAt: .now)
            )
        )
        await sut.onAppear()
        XCTAssertTrue(sut.hasReportedCurrentItem)
    }

    @MainActor
    func test_onReportSubmitted_whenSucceeds_marksAsReported() async {
        let sut = makeSUT(item: makeFoodItem(kind: .catalogue))
        sut.reportReasonText = "wrong calories"
        await sut.onReportSubmitted()
        XCTAssertTrue(sut.hasReportedCurrentItem)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onReportSubmitted_whenReasonTooLong_showsAlertAndDoesNotMarkAsReported() async {
        let sut = makeSUT(
            item: makeFoodItem(kind: .catalogue),
            submitFoodItemReport: SubmitFoodItemReportUseCaseFake(errorToThrow: FoodItemReportError.reasonTooLong)
        )
        sut.reportReasonText = String(repeating: "a", count: 501)
        await sut.onReportSubmitted()
        XCTAssertFalse(sut.hasReportedCurrentItem)
        XCTAssertEqual(sut.alertItem?.title, L10n.FoodItemReport.errorReasonTooLong)
    }

    // MARK: - onShowAddPortionForm

    @MainActor
    func test_onShowAddPortionForm_whenCurrentUnitIsNotAPortion_prefillsGramsFromCurrentQuantity() {
        let sut = makeSUT(quantity: 150, unit: .grams)
        sut.onShowAddPortionForm()
        XCTAssertEqual(sut.newPortionGramsText, "150")
        XCTAssertEqual(sut.newPortionName, "")
        XCTAssertTrue(sut.isAddPortionFormVisible)
    }

    @MainActor
    func test_onShowAddPortionForm_whenCurrentUnitIsAlreadyAPortion_leavesGramsBlank() {
        let existingPortion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(quantity: 2, unit: .portion(existingPortion))
        sut.onShowAddPortionForm()
        XCTAssertEqual(
            sut.newPortionGramsText,
            "",
            "the current grams are a multiple of an existing portion, not a freeform weight worth copying into a new one"
        )
    }

    // MARK: - onAddPersonalPortion

    @MainActor
    func test_onAddPersonalPortion_whenSaveSucceeds_appendsToPersonalPortionsAndClosesForm() async {
        let sut = makeSUT()
        sut.newPortionName = "1 balení"
        sut.newPortionGramsText = "33"
        sut.isAddPortionFormVisible = true
        await sut.onAddPersonalPortion()
        XCTAssertEqual(sut.personalPortions, [FoodPortionDomain(name: "1 balení", grams: 33)])
        XCTAssertNil(sut.alertItem)
        XCTAssertFalse(sut.isAddPortionFormVisible)
        XCTAssertEqual(sut.newPortionName, "")
        XCTAssertEqual(sut.newPortionGramsText, "")
    }

    @MainActor
    func test_onAddPersonalPortion_whenSaveFails_restoresPreAddSnapshotEvenWithADuplicateNameAndGrams() async {
        let existing = FoodPortionDomain(name: "1 lžíce", grams: 15)
        let sut = makeSUT(
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [existing]),
            saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()
        sut.newPortionName = existing.name
        sut.newPortionGramsText = "15"
        await sut.onAddPersonalPortion()
        XCTAssertEqual(
            sut.personalPortions,
            [existing],
            "a failed save must restore the pre-add snapshot, not filter by (name, grams) equality — " +
                "a value-based filter also drops an already-saved portion that happens to share the new one's name and grams"
        )
        XCTAssertEqual(sut.alertItem?.title, L10n.MyPortions.errorSaveFailed)
    }

    @MainActor
    func test_onAddPersonalPortion_whenGramsFieldIsBlank_showsAlertAndDoesNotSave() async {
        let sut = makeSUT()
        sut.newPortionName = "1 balení"
        sut.newPortionGramsText = ""
        await sut.onAddPersonalPortion()
        XCTAssertTrue(
            sut.personalPortions.isEmpty,
            "an unparseable grams field must not silently save a zero-gram portion"
        )
        XCTAssertEqual(sut.alertItem?.title, L10n.FoodPortion.errorInvalidGrams)
    }

    // MARK: - onDeletePersonalPortion

    @MainActor
    func test_onDeletePersonalPortion_whenSaveSucceeds_removesPortion() async {
        let portion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [portion]))
        await sut.onAppear()
        await sut.onDeletePersonalPortion(portion)
        XCTAssertTrue(sut.personalPortions.isEmpty)
    }

    @MainActor
    func test_onDeletePersonalPortion_whenSaveFails_restoresPortionAndShowsAlert() async {
        let portion = FoodPortionDomain(name: "1 balení", grams: 33)
        let sut = makeSUT(
            fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(stubbedPortions: [portion]),
            saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseFake(shouldThrow: true)
        )
        await sut.onAppear()
        await sut.onDeletePersonalPortion(portion)
        XCTAssertEqual(sut.personalPortions, [portion])
        XCTAssertEqual(sut.alertItem?.title, L10n.MyPortions.errorDeleteFailed)
    }

    // MARK: - Helpers

    private func makeSUT(
        item: FoodItemDomain? = nil,
        saveFoodConsumed: any SaveFoodConsumedUseCaseProtocol = SaveFoodConsumedUseCaseFake(),
        fetchMealTypes: any FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        selectedDate: Date = .now,
        mealTypes: [MealTypeDomain] = [],
        isFavourite: Bool = false,
        addFavouriteFood: any AddFavouriteFoodUseCaseProtocol = AddFavouriteFoodUseCaseFake(),
        removeFavouriteFood: any RemoveFavouriteFoodUseCaseProtocol = RemoveFavouriteFoodUseCaseFake(),
        fetchFoodItemPersonalPortions: any FetchFoodItemPersonalPortionsUseCaseProtocol = FetchFoodItemPersonalPortionsUseCaseFake(),
        saveFoodItemPersonalPortions: any SaveFoodItemPersonalPortionsUseCaseProtocol = SaveFoodItemPersonalPortionsUseCaseFake(),
        fetchMyFoodItemReport: any FetchMyFoodItemReportUseCaseProtocol = FetchMyFoodItemReportUseCaseFake(),
        submitFoodItemReport: any SubmitFoodItemReportUseCaseProtocol = SubmitFoodItemReportUseCaseFake(),
        onSaved: @escaping () -> Void = {},
        onFavouriteChanged: @escaping (String, Bool) -> Void = { _, _ in },
        quantity: Double = 1,
        unit: FoodQuantityUnit = .hundredGrams
    ) -> FoodQuantityViewModel {
        let sut = FoodQuantityViewModel(
            item: item ?? makeFoodItem(),
            saveFoodConsumed: saveFoodConsumed,
            fetchMealTypes: fetchMealTypes,
            selectedDate: selectedDate,
            mealTypes: mealTypes,
            isFavourite: isFavourite,
            addFavouriteFood: addFavouriteFood,
            removeFavouriteFood: removeFavouriteFood,
            fetchFoodItemPersonalPortions: fetchFoodItemPersonalPortions,
            saveFoodItemPersonalPortions: saveFoodItemPersonalPortions,
            fetchMyFoodItemReport: fetchMyFoodItemReport,
            submitFoodItemReport: submitFoodItemReport,
            onSaved: onSaved,
            onFavouriteChanged: onFavouriteChanged,
            quantity: quantity,
            unit: unit
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "FoodQuantityViewModel leaked — potential retain cycle")
        }
        return sut
    }

    private func makeMealType(id: String, hour: Int, endHour: Int, minute: Int = 0) -> MealTypeDomain {
        let cal = Calendar.current
        let base = Date.now
        let start = cal.date(bySettingHour: hour, minute: minute, second: 0, of: base) ?? base
        let end = cal.date(bySettingHour: endHour, minute: minute, second: 0, of: base) ?? base
        return MealTypeDomain(id: id, name: "Meal \(id)", startTime: start, endTime: end)
    }

    private func makeFoodItem(
        kind: FoodItemKind = .catalogue,
        caloriesPerHundredGrams: Double = 100,
        fiber: Double? = 0,
        portions: [FoodPortionDomain] = []
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: "test",
            kind: kind,
            czName: "Tvaroh",
            engName: "Cottage cheese",
            weight: 100,
            date: .now,
            energyKJ: 400,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: 2,
            fatSaturated: 1,
            fatUnsaturatedFattyAcids: 1,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: fiber,
            protein: 13,
            salt: 0.1,
            portions: portions
        )
    }
}

private final class SaveFoodConsumedUseCaseSpy: SaveFoodConsumedUseCaseProtocol {

    // MARK: - Properties

    private(set) var wasCalled = false
    private(set) var capturedMealTypeId: String?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, grams: Double, date: Date, mealTypeId: String?) async throws {
        wasCalled = true
        capturedMealTypeId = mealTypeId
    }
}
