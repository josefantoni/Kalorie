//
//  DailyGoalsViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.11.2026.
//

import XCTest
@testable import Kalorie

final class DailyGoalsViewModelTests: XCTestCase {

    // MARK: - Prefill

    func test_init_percentGoalWithTwoDecimals_prefillsLosslessAndCanSave() {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: .percent)
        goal.proteinPercent = 33.33
        goal.carbsPercent = 33.33
        goal.fatPercent = 33.34
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertEqual(sut.protein, 33.33)
        XCTAssertEqual(sut.fat, 33.34)
        XCTAssertFalse(sut.isPercentSumInvalid)
        XCTAssertFalse(sut.canSave, "nothing has changed yet")
        sut.kcalText = "2100"
        XCTAssertTrue(sut.canSave)
    }

    func test_canSave_isDisabledUntilAnInputChangesAndAgainWhenItIsChangedBack() {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: .percent)
        goal.proteinPercent = 20
        goal.carbsPercent = 50
        goal.fatPercent = 30
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertFalse(sut.canSave)
        sut.kcalText = "2100"
        XCTAssertTrue(sut.canSave)
        sut.kcalText = "2000"
        XCTAssertFalse(sut.canSave)
        sut.activityLevel = .high
        XCTAssertTrue(sut.canSave)
    }

    func test_canSave_prefillingTheLatestWeightDoesNotCountAsAChange() async {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: .percent)
        goal.proteinPercent = 20
        goal.carbsPercent = 50
        goal.fatPercent = 30
        let sut = makeSUT(
            history: DailyGoalHistory(entries: [goal]),
            fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedWeights: [BodyWeightDomain(dayKey: "2026-10-01", kilograms: 81.3)])
        )
        await sut.onAppear()
        XCTAssertEqual(sut.weightKilograms, 81.3)
        XCTAssertFalse(sut.canSave)
    }

    func test_canSave_prefillingTheLatestWeightWithoutAnActiveGoalDoesNotCountAsAChange() async {
        var entry = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: nil)
        entry.sex = .male
        entry.birthYear = 1990
        entry.heightCm = 180
        let sut = makeSUT(
            history: DailyGoalHistory(entries: [entry]),
            fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedWeights: [BodyWeightDomain(dayKey: "2026-10-01", kilograms: 81.3)])
        )
        await sut.onAppear()
        XCTAssertEqual(sut.weightKilograms, 81.3)
        XCTAssertNotNil(sut.estimate)
        XCTAssertFalse(sut.canSave)
    }

    func test_canSave_prefillingTheLatestWeightAfterTypingKcalStaysEnabledAndKeepsTheTypedValue() async {
        var entry = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: nil)
        entry.sex = .male
        entry.birthYear = 1990
        entry.heightCm = 180
        let sut = makeSUT(
            history: DailyGoalHistory(entries: [entry]),
            fetchBodyWeights: FetchBodyWeightsUseCaseFake(stubbedWeights: [BodyWeightDomain(dayKey: "2026-10-01", kilograms: 81.3)])
        )
        sut.kcalText = "2345"
        await sut.onAppear()
        XCTAssertEqual(sut.kcalText, "2345")
        XCTAssertTrue(sut.canSave)
    }

    // MARK: - Weight logging

    func test_save_withoutTouchingTheWeight_doesNotLogItAsTodaysMeasurement() async {
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]), saveBodyWeight: recorder)
        sut.kcalText = "2100"
        await sut.onSaveTapped()
        XCTAssertTrue(sut.didFinish)
        XCTAssertTrue(recorder.loggedKilograms.isEmpty)
    }

    func test_save_afterEditingTheWeight_logsTheNewWeight() async {
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]), saveBodyWeight: recorder)
        sut.weightKilograms = 88
        await sut.onSaveTapped()
        XCTAssertEqual(recorder.loggedKilograms, [88])
    }

    func test_save_afterOpeningThePickerOnTheDefaultWeight_doesNotLogIt() async {
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(saveBodyWeight: recorder)
        sut.onWeightTapped()
        await sut.onSaveTapped()
        XCTAssertTrue(sut.didFinish)
        XCTAssertTrue(recorder.loggedKilograms.isEmpty)
    }

    func test_save_afterPickingTheDefaultWeight_logsIt() async {
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(saveBodyWeight: recorder)
        sut.onWeightTapped()
        sut.onWeightChanged(Constants.BodyWeight.defaultKilograms)
        await sut.onSaveTapped()
        XCTAssertEqual(recorder.loggedKilograms, [Constants.BodyWeight.defaultKilograms])
    }

    func test_onAppear_weightTappedBeforeTheFetchReturns_prefillsTheLatestWeightAndDoesNotLogIt() async {
        let recorder = SaveBodyWeightRecorder()
        let fetch = SuspendedFetchBodyWeights(weights: [BodyWeightDomain(dayKey: "2026-11-09", kilograms: 82.5)])
        let sut = makeSUT(fetchBodyWeights: fetch, saveBodyWeight: recorder)
        let task = Task { await sut.onAppear() }
        await fetch.waitUntilRequested()
        sut.onWeightTapped()
        fetch.resume()
        await task.value
        XCTAssertEqual(sut.weightKilograms, 82.5)
        await sut.onSaveTapped()
        XCTAssertTrue(recorder.loggedKilograms.isEmpty)
    }

    func test_onAppear_weightPickedBeforeTheFetchReturns_keepsThePickedWeight() async {
        let fetch = SuspendedFetchBodyWeights(weights: [BodyWeightDomain(dayKey: "2026-11-09", kilograms: 82.5)])
        let sut = makeSUT(fetchBodyWeights: fetch)
        let task = Task { await sut.onAppear() }
        await fetch.waitUntilRequested()
        sut.onWeightTapped()
        sut.onWeightChanged(91)
        fetch.resume()
        await task.value
        XCTAssertEqual(sut.weightKilograms, 91)
    }

    func test_save_poundsWeightRoundTrippedThroughThePicker_doesNotLogIt() async {
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(
            history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]),
            unit: .lb,
            saveBodyWeight: recorder
        )
        sut.onWeightChanged(BodyWeightUnit.lb.kilograms(from: Double(BodyWeightUnit.lb.tenths(kilograms: 80)) / 10))
        sut.kcalText = "2100"
        await sut.onSaveTapped()
        XCTAssertTrue(sut.didFinish)
        XCTAssertTrue(recorder.loggedKilograms.isEmpty)
    }

    func test_save_kilogramWeightRoundedToTheDisplayedTenth_doesNotLogIt() async {
        var goal = profileGoal(macroMode: .percent)
        goal.weightKg = 80.25
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]), saveBodyWeight: recorder)
        sut.onWeightChanged(80.3)
        sut.kcalText = "2100"
        await sut.onSaveTapped()
        XCTAssertTrue(recorder.loggedKilograms.isEmpty)
    }

    func test_save_weightMovedByOneTenth_logsIt() async {
        let recorder = SaveBodyWeightRecorder()
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]), saveBodyWeight: recorder)
        sut.onWeightChanged(80.1)
        await sut.onSaveTapped()
        XCTAssertEqual(recorder.loggedKilograms, [80.1])
    }

    func test_init_gramsGoalWithTwoDecimals_prefillsLossless() {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: .grams)
        goal.proteinGrams = 152.25
        goal.carbsGrams = 200
        goal.fatGrams = 60
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertEqual(sut.protein, 152.25)
    }

    func test_init_afterEndedGoal_prefillsProfileFromEarlierEntry() {
        var goal = DailyGoalDomain(validFrom: "2026-01-01", kcalGoal: 1900, macroMode: .percent)
        goal.sex = .female
        goal.birthYear = 1990
        goal.heightCm = 165
        goal.weightKg = 60
        goal.activityLevel = .light
        let history = DailyGoalHistory(entries: [goal, .ended(validFrom: "2026-02-01")])
        let sut = makeSUT(history: history)
        XCTAssertFalse(sut.hasActiveGoal)
        XCTAssertEqual(sut.sex, .female)
        XCTAssertEqual(sut.birthYear, 1990)
        XCTAssertEqual(sut.heightCm, 165)
        XCTAssertEqual(sut.weightKilograms, 60)
        XCTAssertEqual(sut.activityLevel, .light)
    }

    // MARK: - Macro mode

    func test_macroModeToGrams_withEmptyKcal_keepsPercentModeAndTexts() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .percent)
        XCTAssertEqual(sut.proteinText, "20")
        XCTAssertEqual(sut.carbsText, "50")
        XCTAssertEqual(sut.fatText, "30")
    }

    func test_macroModeToGrams_withEmptyKcal_publishesChangeSoTheControlSnapsBack() {
        let sut = makeSUT()
        sut.kcalText = ""
        var changeCount = 0
        let cancellable = sut.objectWillChange.sink { changeCount += 1 }
        sut.onMacroModeChanged(.grams)
        XCTAssertGreaterThanOrEqual(changeCount, 1)
        cancellable.cancel()
    }

    func test_macroModeHint_isNilUntilASwitchIsRejected() {
        let sut = makeSUT()
        sut.carbsText = ""
        XCTAssertNil(sut.macroModeHint)
    }

    func test_macroModeHint_afterRejectedSwitchWithOneEmptyMacro_asksForAllThreeMacros() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.carbsText = ""
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeIncomplete)
    }

    func test_macroModeHint_afterRejectedSwitchToGramsWithEmptyKcal_asksForKcal() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeNeedsKcal)
    }

    func test_macroModeHint_inGramsModeWithKcalCleared_showsAfterSwitchingToPercentAndBack() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.kcalText = ""
        sut.onMacroModeChanged(.percent)
        XCTAssertEqual(sut.macroMode, .percent)
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .percent)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeNeedsKcal)
    }

    func test_rejectedMacroModeSwitch_requestsKcalFocusOnEveryRejection() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.onMacroModeChanged(.grams)
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.kcalFocusRequestCount, 2)
    }

    func test_acceptedMacroModeSwitch_doesNotRequestKcalFocus() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.kcalFocusRequestCount, 0)
    }

    func test_macroModeHint_disappearsOnceTheBlockingFieldIsFilled() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.onMacroModeChanged(.grams)
        sut.kcalText = "2000"
        XCTAssertNil(sut.macroModeHint)
    }

    func test_macroModeHint_disappearsAfterASuccessfulSwitch() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.onMacroModeChanged(.grams)
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .grams)
        XCTAssertNil(sut.macroModeHint)
    }

    func test_macroModeHint_doesNotReturnWithoutANewSwitchAttempt() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.onMacroModeChanged(.grams)
        sut.kcalText = "2000"
        sut.kcalText = ""
        XCTAssertNil(sut.macroModeHint)
    }

    func test_macroModeHint_incompleteMacrosDoesNotReturnWithoutANewSwitchAttempt() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.carbsText = ""
        sut.onMacroModeChanged(.grams)
        sut.carbsText = "50"
        sut.carbsText = ""
        XCTAssertNil(sut.macroModeHint)
    }

    func test_macroModeHint_doesNotTurnIntoTheSumMessageWhenKcalIsTyped() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.proteinText = "30"
        sut.carbsText = "30"
        sut.fatText = "30"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeNeedsKcal)

        sut.kcalText = "2000"
        XCTAssertNil(sut.macroModeHint)
    }

    func test_macroModeHint_doesNotTurnIntoTheNeedsKcalMessageWhenTheLastMacroIsFilled() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.proteinText = "20"
        sut.carbsText = "50"
        sut.fatText = ""
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeIncomplete)

        sut.fatText = "30"
        XCTAssertNil(sut.macroModeHint)
        XCTAssertEqual(sut.kcalFocusRequestCount, 0)
    }

    func test_rejectedMacroModeSwitch_withIncompleteMacros_doesNotRequestKcalFocus() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.carbsText = ""
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeIncomplete)
        XCTAssertEqual(sut.kcalFocusRequestCount, 0)
    }

    func test_macroModeToGrams_withPercentsNotSummingTo100_keepsPercentModeAndShowsHint() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.proteinText = "20"
        sut.carbsText = "50"
        sut.fatText = "20"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .percent)
        XCTAssertEqual(sut.carbsText, "50")
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeSumNot100)
        XCTAssertEqual(sut.kcalFocusRequestCount, 0)
    }

    func test_macroModeHint_sumNot100_disappearsOnceThePercentsSumTo100() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.fatText = "20"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .percent)
        sut.fatText = "30"
        XCTAssertNil(sut.macroModeHint)
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .grams)
    }

    func test_macroModeToGrams_withEmptyKcalAndPercentsNotSummingTo100_asksForKcalFirst() {
        let sut = makeSUT()
        sut.kcalText = ""
        sut.fatText = "20"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroModeHint, L10n.Goals.hintMacroModeNeedsKcal)
    }

    func test_kcalOfEnteredGramsDifferingFromGoal_gramsMatchingTheGoal_isNil() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.proteinText = "100"
        sut.carbsText = "200"
        sut.fatText = "100"
        XCTAssertEqual(sut.kcalText, "2100")
        XCTAssertNil(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    func test_kcalOfEnteredGramsDifferingFromGoal_kcalGoalTypedDifferentFromGrams_isTheGramsKcal() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.proteinText = "100"
        sut.carbsText = "200"
        sut.fatText = "100"
        sut.kcalText = "2500"
        XCTAssertEqual(sut.kcalOfEnteredGramsDifferingFromGoal, 2100)
    }

    func test_kcalOfEnteredGramsDifferingFromGoal_afterSwitchingASplitToGrams_ignoresTheRoundingOfWholeGrams() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.proteinText = "20"
        sut.carbsText = "50"
        sut.fatText = "30"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .grams)
        XCTAssertEqual(sut.kcalOfEnteredGrams, 2003)
        XCTAssertNil(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    func test_kcalOfEnteredGramsDifferingFromGoal_justBeyondTheTolerance_isTheGramsKcal() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.proteinText = "100"
        sut.carbsText = "200"
        sut.fatText = "100"
        sut.kcalText = "2090"
        XCTAssertNil(sut.kcalOfEnteredGramsDifferingFromGoal)
        sut.kcalText = "2089"
        XCTAssertEqual(sut.kcalOfEnteredGramsDifferingFromGoal, 2100)
    }

    func test_kcalOfEnteredGramsDifferingFromGoal_withEmptyKcalGoal_isNil() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.kcalText = ""
        XCTAssertNil(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    func test_kcalOfEnteredGramsDifferingFromGoal_inPercentMode_isNil() {
        let sut = makeSUT()
        sut.kcalText = "2500"
        XCTAssertNil(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    func test_onEndConfirmed_whileSaving_isIgnored() async {
        let save = SuspendedSaveDailyGoal()
        let sut = makeSUT(saveDailyGoal: save)
        sut.kcalText = "2100"
        let saving = Task { await sut.onSaveTapped() }
        await save.waitUntilRequested()
        await sut.onEndConfirmed()
        save.resume()
        await saving.value
        XCTAssertEqual(save.goals.count, 1)
        XCTAssertEqual(save.goals.first?.kcalGoal, 2100)
    }

    func test_macroModeToGrams_withOneEmptyMacro_keepsPercentModeAndTexts() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.carbsText = ""
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .percent)
        XCTAssertEqual(sut.proteinText, "20")
        XCTAssertEqual(sut.carbsText, "")
    }

    func test_macroModeToPercent_withOneEmptyMacro_keepsGramsModeAndTexts() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.fatText = ""
        sut.onMacroModeChanged(.percent)
        XCTAssertEqual(sut.macroMode, .grams)
        XCTAssertEqual(sut.proteinText, "100")
        XCTAssertEqual(sut.fatText, "")
    }

    func test_macroModeToGrams_withKcal_convertsTexts() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        XCTAssertEqual(sut.macroMode, .grams)
        XCTAssertEqual(sut.proteinText, "100")
    }

    // MARK: - Validation

    func test_canSave_negativeMacroSummingTo100_isFalse() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.proteinText = "120"
        sut.carbsText = "-20"
        sut.fatText = "0"
        XCTAssertFalse(sut.isPercentSumInvalid)
        XCTAssertFalse(sut.canSave)
    }

    func test_canSave_gramsModeAboveLimit_isFalse() {
        let sut = makeSUT()
        sut.kcalText = "2000"
        sut.onMacroModeChanged(.grams)
        sut.proteinText = "1500"
        sut.carbsText = "0"
        sut.fatText = "0"
        XCTAssertTrue(SaveDailyGoalUseCase.kcalRange.contains(sut.kcal ?? 0))
        XCTAssertFalse(sut.canSave)
        sut.proteinText = "150"
        XCTAssertTrue(sut.canSave)
    }

    // MARK: - Helpers

    // MARK: - Collapsible sections

    func test_sections_withStoredBodyDataAndActivity_startCollapsedAndToggle() {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: .percent)
        goal.sex = .female
        goal.birthYear = 1990
        goal.heightCm = 165
        goal.weightKg = 60
        goal.activityLevel = .light
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertTrue(sut.isBodyDataRemembered)
        XCTAssertTrue(sut.isActivityRemembered)
        XCTAssertTrue(sut.isBodyDataCollapsed)
        XCTAssertTrue(sut.isActivityCollapsed)

        sut.onBodyDataToggled()
        sut.onActivityToggled()
        XCTAssertFalse(sut.isBodyDataCollapsed)
        XCTAssertFalse(sut.isActivityCollapsed)
    }

    func test_sections_withoutStoredValues_areNotCollapsible() {
        let sut = makeSUT()
        XCTAssertFalse(sut.isBodyDataRemembered)
        XCTAssertFalse(sut.isActivityRemembered)
        XCTAssertFalse(sut.isBodyDataCollapsed)
        XCTAssertFalse(sut.isActivityCollapsed)
    }

    func test_activity_withoutStoredValue_defaultsToLightWithoutCountingAsAChange() {
        let sut = makeSUT()
        XCTAssertEqual(sut.activityLevel, .light)
        XCTAssertFalse(sut.isActivityRemembered, "the default must not collapse the section")
        XCTAssertFalse(sut.canSave)
    }

    func test_sections_withPartialBodyData_keepBodyDataOpenButCollapseKnownActivity() {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: .percent)
        goal.sex = .male
        goal.activityLevel = .high
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertFalse(sut.isBodyDataCollapsed)
        XCTAssertTrue(sut.isActivityCollapsed)
    }

    func test_sexChange_inGramsMode_rescalesGramsToTheNewReferenceKcal() throws {
        let sut = makeSUT()
        sut.onMacroModeChanged(.grams)
        sut.sex = .female
        XCTAssertEqual(sut.kcalText, "1900")
        let kcalOfGrams = try XCTUnwrap(sut.kcalOfEnteredGrams)
        XCTAssertEqual(kcalOfGrams, 1900, accuracy: 10)
    }

    func test_variantSliderReleased_withoutMatchingVariant_appliesTheVariantUnderTheThumb() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        let variants = try XCTUnwrap(sut.estimate?.variants)
        sut.kcalText = "1234"
        sut.onVariantSliderReleased()
        let index = Int(sut.selectedVariantIndex)
        XCTAssertEqual(sut.kcal, Double(variants[index].kcal))
        XCTAssertEqual(sut.matchingVariantIndex, index)
    }

    func test_variantSliderReleased_withMatchingVariant_keepsKcal() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        sut.onVariantSelected(0)
        let kcal = sut.kcal
        sut.onVariantSliderReleased()
        XCTAssertEqual(sut.kcal, kcal)
    }

    // MARK: - Variants

    func test_variantSelected_writesTheVariantKcalAtOnceAndOnlyThenMatches() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        let variants = try XCTUnwrap(sut.estimate?.variants)
        XCTAssertNil(sut.matchingVariantIndex, "a goal typed by hand matches no variant")

        sut.onVariantSelected(0)
        XCTAssertEqual(sut.kcal, Double(variants[0].kcal))
        XCTAssertEqual(sut.matchingVariantIndex, 0)
        XCTAssertLessThan(variants[0].kcal, variants[2].kcal)

        sut.kcalText = "1234"
        XCTAssertNil(sut.matchingVariantIndex)
    }

    func test_variantSelected_inGramsMode_keepsTheMacroSplitAndRescalesTheGrams() throws {
        var goal = profileGoal(macroMode: .grams)
        goal.proteinGrams = 150
        goal.carbsGrams = 250
        goal.fatGrams = 67
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        let variants = try XCTUnwrap(sut.estimate?.variants)
        let before = try XCTUnwrap(sut.protein)

        sut.onVariantSelected(4)
        XCTAssertEqual(sut.kcal, Double(variants[4].kcal))
        XCTAssertGreaterThan(try XCTUnwrap(sut.protein), before)
        let kcalOfGrams = try XCTUnwrap(sut.kcalOfEnteredGrams)
        XCTAssertEqual(kcalOfGrams, Double(variants[4].kcal), accuracy: 30)
    }

    func test_variantSelected_inGramsMode_returningToAVariantRestoresTheSameGrams() throws {
        var goal = profileGoal(macroMode: .grams)
        goal.proteinGrams = 10
        goal.carbsGrams = 20
        goal.fatGrams = 3
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))

        sut.onVariantSelected(2)
        let expected = [sut.proteinText, sut.carbsText, sut.fatText]
        sut.onVariantSelected(0)
        sut.onVariantSelected(4)
        sut.onVariantSelected(1)
        sut.onVariantSelected(2)
        XCTAssertEqual([sut.proteinText, sut.carbsText, sut.fatText], expected)
    }

    func test_variantSelected_inGramsMode_afterEditingAMacro_rescalesFromTheEditedSplit() throws {
        var goal = profileGoal(macroMode: .grams)
        goal.proteinGrams = 10
        goal.carbsGrams = 20
        goal.fatGrams = 3
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))

        sut.onVariantSelected(0)
        sut.proteinText = "400"
        let carbs = try XCTUnwrap(sut.carbs)
        let fat = try XCTUnwrap(sut.fat)
        let expectedShare = 1600 / (1600 + carbs * 4 + fat * 9)
        sut.onVariantSelected(4)
        let protein = try XCTUnwrap(sut.protein) * 4
        let total = try XCTUnwrap(sut.kcalOfEnteredGrams)
        XCTAssertEqual(protein / total, expectedShare, accuracy: 0.02)
    }

    func test_activityChange_reappliesTheVariantUnderTheSlider() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        sut.onVariantSelected(3)
        let before = try XCTUnwrap(sut.kcal)

        sut.activityLevel = .high
        let variants = try XCTUnwrap(sut.estimate?.variants)
        XCTAssertEqual(sut.kcal, Double(variants[3].kcal))
        XCTAssertGreaterThan(try XCTUnwrap(sut.kcal), before)
        XCTAssertEqual(sut.matchingVariantIndex, 3)
    }

    func test_step_inGramsMode_afterReopeningAGoalSavedAtAVariant_keepsTheSliderOnThatVariant() throws {
        var goal = profileGoal(macroMode: .grams)
        goal.proteinGrams = 150
        goal.carbsGrams = 250
        goal.fatGrams = 67
        let probe = makeSUT(history: DailyGoalHistory(entries: [goal]))
        goal.kcalGoal = Double(try XCTUnwrap(probe.estimate?.variants[3].kcal))
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertEqual(sut.matchingVariantIndex, 3)

        sut.onStep(.protein, direction: 1)
        XCTAssertNil(sut.matchingVariantIndex)
        XCTAssertEqual(sut.selectedVariantIndex, 3)
    }

    func test_activityChange_afterReopeningAGoalSavedAtAVariant_keepsThatVariant() throws {
        var goal = profileGoal(macroMode: .percent)
        let probe = makeSUT(history: DailyGoalHistory(entries: [goal]))
        goal.kcalGoal = Double(try XCTUnwrap(probe.estimate?.variants[3].kcal))
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        XCTAssertEqual(sut.matchingVariantIndex, 3)

        sut.activityLevel = .high
        XCTAssertEqual(sut.matchingVariantIndex, 3)
    }

    func test_activityChange_afterTypingAKcalThatMatchesAVariant_appliesThatVariant() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        sut.onVariantSelected(4)
        sut.kcalText = String(try XCTUnwrap(sut.estimate?.variants[1].kcal))
        XCTAssertEqual(sut.matchingVariantIndex, 1)

        sut.activityLevel = .high
        XCTAssertEqual(sut.matchingVariantIndex, 1)
    }

    func test_activityChange_inGramsMode_scalesTheGramsWithTheNewKcal() throws {
        var goal = profileGoal(macroMode: .grams)
        goal.proteinGrams = 150
        goal.carbsGrams = 250
        goal.fatGrams = 67
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        sut.onVariantSelected(3)
        let proteinBefore = try XCTUnwrap(sut.protein)

        sut.activityLevel = .high
        let variants = try XCTUnwrap(sut.estimate?.variants)
        XCTAssertEqual(sut.kcal, Double(variants[3].kcal))
        XCTAssertGreaterThan(try XCTUnwrap(sut.protein), proteinBefore)
        XCTAssertEqual(try XCTUnwrap(sut.kcalOfEnteredGrams), Double(variants[3].kcal), accuracy: 30)
    }

    func test_activityChange_withoutEstimate_keepsTheKcalGoal() {
        let sut = makeSUT()
        sut.kcalText = "2345"
        sut.activityLevel = .high
        XCTAssertEqual(sut.kcalText, "2345")
    }

    func test_sexChange_reappliesTheVariantUnderTheSliderToTheNewEstimate() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        sut.onVariantSelected(3)
        let before = try XCTUnwrap(sut.kcal)

        sut.sex = .female
        let variants = try XCTUnwrap(sut.estimate?.variants)
        XCTAssertEqual(sut.kcal, Double(variants[3].kcal))
        XCTAssertLessThan(try XCTUnwrap(sut.kcal), before)
        XCTAssertEqual(sut.matchingVariantIndex, 3)
    }

    func test_sexChange_overwritesAKcalTypedByHand() throws {
        let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
        sut.kcalText = "1234"

        sut.sex = .female
        XCTAssertNotNil(sut.matchingVariantIndex)
        XCTAssertNotEqual(sut.kcalText, "1234")
    }

    func test_sexChange_withoutEstimate_overwritesATypedKcalWithTheReference() {
        let sut = makeSUT()
        sut.kcalText = "1900"
        sut.sex = .female
        sut.kcalText = "1900"
        sut.sex = .male
        XCTAssertEqual(sut.kcalText, "2400")
    }

    func test_sexChange_inGramsModeWithEstimate_scalesTheGramsWithTheNewKcal() throws {
        var goal = profileGoal(macroMode: .grams)
        goal.proteinGrams = 150
        goal.carbsGrams = 250
        goal.fatGrams = 67
        let sut = makeSUT(history: DailyGoalHistory(entries: [goal]))
        sut.onVariantSelected(3)
        let proteinBefore = try XCTUnwrap(sut.protein)

        sut.sex = .female
        let variants = try XCTUnwrap(sut.estimate?.variants)
        XCTAssertEqual(sut.kcal, Double(variants[3].kcal))
        XCTAssertLessThan(try XCTUnwrap(sut.protein), proteinBefore)
        XCTAssertEqual(try XCTUnwrap(sut.kcalOfEnteredGrams), Double(variants[3].kcal), accuracy: 30)
    }

    func test_macroModeSwitch_withNonPositiveOrNonFiniteMacro_isRejected() {
        for invalid in ["-10", "nan", "inf"] {
            let sut = makeSUT(history: DailyGoalHistory(entries: [profileGoal(macroMode: .percent)]))
            sut.proteinText = invalid
            sut.carbsText = "60"
            sut.fatText = "50"
            sut.onMacroModeChanged(.grams)
            XCTAssertEqual(sut.macroMode, .percent, "\(invalid) must not pass as a macro")
            XCTAssertEqual(sut.rejectedMacroMode, .grams)
        }
    }

    func test_init_afterAnEndedGoalWithAProfile_prefillsTheMaintainingEstimateInsteadOfTheReference() throws {
        let history = DailyGoalHistory(entries: [
            profileGoal(macroMode: .percent),
            .ended(validFrom: "2026-11-05")
        ])
        let sut = makeSUT(history: history)
        XCTAssertFalse(sut.hasActiveGoal)
        let variants = try XCTUnwrap(sut.estimate?.variants)
        let maintaining = try XCTUnwrap(GoalEstimate.percentChanges.firstIndex(of: 0))
        XCTAssertEqual(sut.kcal, Double(variants[maintaining].kcal))
        XCTAssertEqual(sut.matchingVariantIndex, maintaining)
        XCTAssertNotEqual(sut.kcalText, "2400")
    }

    func test_bodyDataCompleted_withTheReferencePrefill_appliesTheVariantUnderTheSlider() throws {
        let sut = makeSUT()
        XCTAssertEqual(sut.kcalText, "2400")
        sut.birthYear = 1990
        sut.heightCm = 180
        XCTAssertEqual(sut.kcalText, "2400")
        sut.weightKilograms = 80

        let variants = try XCTUnwrap(sut.estimate?.variants)
        let index = Int(sut.selectedVariantIndex)
        XCTAssertEqual(sut.kcal, Double(variants[index].kcal))
        XCTAssertEqual(sut.matchingVariantIndex, index)
    }

    func test_bodyDataCompleted_afterTypingKcal_keepsTheTypedKcal() {
        let sut = makeSUT()
        sut.kcalText = "1999"
        sut.birthYear = 1990
        sut.heightCm = 180
        sut.weightKilograms = 80
        XCTAssertEqual(sut.kcalText, "1999")
    }

    func test_bodyDataCompleted_inGramsMode_scalesTheGramsWithTheNewKcal() throws {
        let sut = makeSUT()
        sut.onMacroModeChanged(.grams)
        sut.birthYear = 1990
        sut.heightCm = 180
        sut.weightKilograms = 80
        let kcal = try XCTUnwrap(sut.kcal)
        XCTAssertEqual(try XCTUnwrap(sut.kcalOfEnteredGrams), kcal, accuracy: 30)
    }

    func test_init_withoutAnyProfile_prefillsTheReference() {
        let sut = makeSUT()
        XCTAssertEqual(sut.kcalText, "2400")
    }

    private func profileGoal(macroMode: GoalMacroMode) -> DailyGoalDomain {
        var goal = DailyGoalDomain(validFrom: "2026-11-01", kcalGoal: 2000, macroMode: macroMode)
        goal.sex = .male
        goal.birthYear = 1990
        goal.heightCm = 180
        goal.weightKg = 80
        goal.activityLevel = .moderate
        if macroMode == .percent {
            goal.proteinPercent = 20
            goal.carbsPercent = 50
            goal.fatPercent = 30
        }
        return goal
    }

    private func makeSUT(
        history: DailyGoalHistory = .empty,
        unit: BodyWeightUnit = .kg,
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol = FetchBodyWeightsUseCaseFake(),
        saveBodyWeight: any SaveBodyWeightUseCaseProtocol = SaveBodyWeightUseCaseFake(),
        saveDailyGoal: any SaveDailyGoalUseCaseProtocol = SaveDailyGoalUseCaseFake()
    ) -> DailyGoalsViewModel {
        DailyGoalsViewModel(
            history: history,
            unit: unit,
            fetchBodyWeights: fetchBodyWeights,
            saveDailyGoal: saveDailyGoal,
            saveBodyWeight: saveBodyWeight
        ) { _ in }
    }
}

private final class SuspendedSaveDailyGoal: SaveDailyGoalUseCaseProtocol {
    private(set) var goals: [DailyGoalDomain] = []
    private var continuation: CheckedContinuation<Void, Never>?
    private var requestWaiter: CheckedContinuation<Void, Never>?

    func waitUntilRequested() async {
        guard goals.isEmpty else { return }
        await withCheckedContinuation { requestWaiter = $0 }
    }

    func resume() {
        continuation?.resume()
        continuation = nil
    }

    func callAsFunction(_ goal: DailyGoalDomain) async throws -> DailyGoalHistory {
        goals.append(goal)
        if goals.count == 1 {
            requestWaiter?.resume()
            requestWaiter = nil
            await withCheckedContinuation { continuation = $0 }
        }
        return DailyGoalHistory(entries: [goal])
    }
}

private final class SaveBodyWeightRecorder: SaveBodyWeightUseCaseProtocol {
    private(set) var loggedKilograms: [Double] = []

    func callAsFunction(kilograms: Double, for day: Date) async throws {
        loggedKilograms.append(kilograms)
    }
}

private final class SuspendedFetchBodyWeights: FetchBodyWeightsUseCaseProtocol {
    private let weights: [BodyWeightDomain]
    private var continuation: CheckedContinuation<Void, Never>?
    private var requestWaiter: CheckedContinuation<Void, Never>?
    private var isRequested = false

    init(weights: [BodyWeightDomain]) {
        self.weights = weights
    }

    func waitUntilRequested() async {
        guard !isRequested else { return }
        await withCheckedContinuation { requestWaiter = $0 }
    }

    func resume() {
        continuation?.resume()
        continuation = nil
    }

    func callAsFunction(upTo day: Date) async throws -> [BodyWeightDomain] {
        isRequested = true
        requestWaiter?.resume()
        requestWaiter = nil
        await withCheckedContinuation { continuation = $0 }
        return weights
    }

    func callAsFunction(after day: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(from: Date, to: Date) async throws -> [BodyWeightDomain] { [] }

    func callAsFunction(inYear year: Int) async throws -> [BodyWeightDomain] { [] }
}
