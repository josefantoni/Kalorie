package antoni.kalorie.features.dailygoals

import antoni.kalorie.R
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.models.GoalActivityLevel
import antoni.kalorie.core.models.GoalEstimate
import antoni.kalorie.core.models.GoalMacroMode
import antoni.kalorie.core.models.GoalSex
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseFake
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseProtocol
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseFake
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseProtocol
import antoni.kalorie.core.usecases.SaveDailyGoalUseCase
import antoni.kalorie.core.usecases.SaveDailyGoalUseCaseFake
import antoni.kalorie.core.usecases.SaveDailyGoalUseCaseProtocol
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DailyGoalsViewModelTest {

    // MARK: - Prefill

    @Test
    fun init_percentGoalWithTwoDecimals_prefillsLosslessAndCanSave() {
        val goal = DailyGoalDomain("2026-11-01", kcalGoal = 2000.0, proteinPercent = 33.33, carbsPercent = 33.33, fatPercent = 33.34)
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)))
        assertEquals(33.33, sut.protein)
        assertEquals(33.34, sut.fat)
        assertFalse(sut.isPercentSumInvalid)
        assertFalse("nothing has changed yet", sut.canSave)
        sut.onKcalTextChanged("2100")
        assertTrue(sut.canSave)
    }

    @Test
    fun canSave_isDisabledUntilAnInputChangesAndAgainWhenItIsChangedBack() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(percentGoalWithoutProfile())))
        assertFalse(sut.canSave)
        sut.onKcalTextChanged("2100")
        assertTrue(sut.canSave)
        sut.onKcalTextChanged("2000")
        assertFalse(sut.canSave)
        sut.onActivityLevelChanged(GoalActivityLevel.HIGH)
        assertTrue(sut.canSave)
    }

    @Test
    fun canSave_prefillingTheLatestWeightDoesNotCountAsAChange() = runTest {
        val sut = makeSUT(
            history = DailyGoalHistory(listOf(percentGoalWithoutProfile())),
            fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(BodyWeightDomain("2026-10-01", 81.3))),
        )
        sut.onAppear()
        assertEquals(81.3, sut.weightKilograms.value)
        assertFalse(sut.canSave)
    }

    @Test
    fun canSave_prefillingTheLatestWeightWithoutAnActiveGoalDoesNotCountAsAChange() = runTest {
        val entry = DailyGoalDomain("2026-11-01", sex = GoalSex.MALE, birthYear = 1990, heightCm = 180.0)
        val sut = makeSUT(
            history = DailyGoalHistory(listOf(entry)),
            fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(BodyWeightDomain("2026-10-01", 81.3))),
        )
        sut.onAppear()
        assertEquals(81.3, sut.weightKilograms.value)
        assertNotNull(sut.estimate.value)
        assertFalse(sut.canSave)
    }

    @Test
    fun canSave_prefillingTheLatestWeightAfterTypingKcalStaysEnabledAndKeepsTheTypedValue() = runTest {
        val entry = DailyGoalDomain("2026-11-01", sex = GoalSex.MALE, birthYear = 1990, heightCm = 180.0)
        val sut = makeSUT(
            history = DailyGoalHistory(listOf(entry)),
            fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(BodyWeightDomain("2026-10-01", 81.3))),
        )
        sut.onKcalTextChanged("2345")
        sut.onAppear()
        assertEquals("2345", sut.kcalText.value)
        assertTrue(sut.canSave)
    }

    // MARK: - Weight logging

    @Test
    fun save_withoutTouchingTheWeight_doesNotLogItAsTodaysMeasurement() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))), saveBodyWeight = recorder)
        sut.onKcalTextChanged("2100")
        sut.onSaveTapped()
        assertTrue(sut.didFinish.value)
        assertTrue(recorder.loggedKilograms.isEmpty())
    }

    @Test
    fun save_afterEditingTheWeight_logsTheNewWeight() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))), saveBodyWeight = recorder)
        sut.onWeightChanged(88.0)
        sut.onSaveTapped()
        assertEquals(listOf(88.0), recorder.loggedKilograms)
    }

    @Test
    fun save_afterOpeningThePickerOnTheDefaultWeight_doesNotLogIt() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(saveBodyWeight = recorder)
        sut.onWeightTapped()
        sut.onSaveTapped()
        assertTrue(sut.didFinish.value)
        assertTrue(recorder.loggedKilograms.isEmpty())
    }

    @Test
    fun save_afterPickingTheDefaultWeight_logsIt() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(saveBodyWeight = recorder)
        sut.onWeightTapped()
        sut.onWeightChanged(Constants.BodyWeight.DEFAULT_KILOGRAMS)
        sut.onSaveTapped()
        assertEquals(listOf(Constants.BodyWeight.DEFAULT_KILOGRAMS), recorder.loggedKilograms)
    }

    @Test
    fun onAppear_weightTappedBeforeTheFetchReturns_prefillsTheLatestWeightAndDoesNotLogIt() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val fetch = SuspendedFetchBodyWeights(listOf(BodyWeightDomain("2026-11-09", 82.5)))
        val sut = makeSUT(fetchBodyWeights = fetch, saveBodyWeight = recorder)
        val job = launch { sut.onAppear() }
        runCurrent()
        sut.onWeightTapped()
        fetch.gate.complete(Unit)
        job.join()
        assertEquals(82.5, sut.weightKilograms.value)
        sut.onSaveTapped()
        assertTrue(recorder.loggedKilograms.isEmpty())
    }

    @Test
    fun onAppear_weightPickedBeforeTheFetchReturns_keepsThePickedWeight() = runTest {
        val fetch = SuspendedFetchBodyWeights(listOf(BodyWeightDomain("2026-11-09", 82.5)))
        val sut = makeSUT(fetchBodyWeights = fetch)
        val job = launch { sut.onAppear() }
        runCurrent()
        sut.onWeightTapped()
        sut.onWeightChanged(91.0)
        fetch.gate.complete(Unit)
        job.join()
        assertEquals(91.0, sut.weightKilograms.value)
    }

    @Test
    fun save_poundsWeightRoundTrippedThroughThePicker_doesNotLogIt() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))), unit = BodyWeightUnit.LB, saveBodyWeight = recorder)
        sut.onWeightChanged(BodyWeightUnit.LB.kilograms(BodyWeightUnit.LB.tenths(80.0) / 10.0))
        sut.onKcalTextChanged("2100")
        sut.onSaveTapped()
        assertTrue(sut.didFinish.value)
        assertTrue(recorder.loggedKilograms.isEmpty())
    }

    @Test
    fun save_kilogramWeightRoundedToTheDisplayedTenth_doesNotLogIt() = runTest {
        val goal = profileGoal(GoalMacroMode.PERCENT).copy(weightKg = 80.25)
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)), saveBodyWeight = recorder)
        sut.onWeightChanged(80.3)
        sut.onKcalTextChanged("2100")
        sut.onSaveTapped()
        assertTrue(recorder.loggedKilograms.isEmpty())
    }

    @Test
    fun save_weightMovedByOneTenth_logsIt() = runTest {
        val recorder = SaveBodyWeightRecorder()
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))), saveBodyWeight = recorder)
        sut.onWeightChanged(80.1)
        sut.onSaveTapped()
        assertEquals(listOf(80.1), recorder.loggedKilograms)
    }

    @Test
    fun init_gramsGoalWithTwoDecimals_prefillsLossless() {
        val goal = DailyGoalDomain("2026-11-01", kcalGoal = 2000.0, macroMode = GoalMacroMode.GRAMS, proteinGrams = 152.25, carbsGrams = 200.0, fatGrams = 60.0)
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)))
        assertEquals(152.25, sut.protein)
    }

    @Test
    fun init_afterEndedGoal_prefillsProfileFromEarlierEntry() {
        val goal = DailyGoalDomain(
            "2026-01-01",
            kcalGoal = 1900.0,
            sex = GoalSex.FEMALE,
            birthYear = 1990,
            heightCm = 165.0,
            weightKg = 60.0,
            activityLevel = GoalActivityLevel.LIGHT,
        )
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal, DailyGoalDomain.ended("2026-02-01"))))
        assertFalse(sut.hasActiveGoal)
        assertEquals(GoalSex.FEMALE, sut.sex.value)
        assertEquals(1990, sut.birthYear.value)
        assertEquals(165, sut.heightCm.value)
        assertEquals(60.0, sut.weightKilograms.value)
        assertEquals(GoalActivityLevel.LIGHT, sut.activityLevel.value)
    }

    // MARK: - Macro mode

    @Test
    fun macroModeToGrams_withEmptyKcal_keepsPercentModeAndTexts() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.PERCENT, sut.macroMode.value)
        assertEquals("20", sut.proteinText.value)
        assertEquals("50", sut.carbsText.value)
        assertEquals("30", sut.fatText.value)
    }

    @Test
    fun macroModeHint_isNilUntilASwitchIsRejected() {
        val sut = makeSUT()
        sut.onCarbsTextChanged("")
        assertNull(sut.macroModeHint)
    }

    @Test
    fun macroModeHint_afterRejectedSwitchWithOneEmptyMacro_asksForAllThreeMacros() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onCarbsTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(R.string.goals_hint_macroModeIncomplete, sut.macroModeHint)
    }

    @Test
    fun macroModeHint_afterRejectedSwitchToGramsWithEmptyKcal_asksForKcal() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(R.string.goals_hint_macroModeNeedsKcal, sut.macroModeHint)
    }

    @Test
    fun macroModeHint_inGramsModeWithKcalCleared_showsAfterSwitchingToPercentAndBack() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.PERCENT)
        assertEquals(GoalMacroMode.PERCENT, sut.macroMode.value)
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.PERCENT, sut.macroMode.value)
        assertEquals(R.string.goals_hint_macroModeNeedsKcal, sut.macroModeHint)
    }

    @Test
    fun rejectedMacroModeSwitch_requestsKcalFocusOnEveryRejection() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(2, sut.kcalFocusRequestCount.value)
    }

    @Test
    fun acceptedMacroModeSwitch_doesNotRequestKcalFocus() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(0, sut.kcalFocusRequestCount.value)
    }

    @Test
    fun macroModeHint_disappearsOnceTheBlockingFieldIsFilled() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onKcalTextChanged("2000")
        assertNull(sut.macroModeHint)
    }

    @Test
    fun macroModeHint_disappearsAfterASuccessfulSwitch() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.GRAMS, sut.macroMode.value)
        assertNull(sut.macroModeHint)
    }

    @Test
    fun macroModeHint_doesNotReturnWithoutANewSwitchAttempt() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onKcalTextChanged("2000")
        sut.onKcalTextChanged("")
        assertNull(sut.macroModeHint)
    }

    @Test
    fun macroModeHint_incompleteMacrosDoesNotReturnWithoutANewSwitchAttempt() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onCarbsTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onCarbsTextChanged("50")
        sut.onCarbsTextChanged("")
        assertNull(sut.macroModeHint)
    }

    @Test
    fun macroModeHint_doesNotTurnIntoTheSumMessageWhenKcalIsTyped() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onProteinTextChanged("30")
        sut.onCarbsTextChanged("30")
        sut.onFatTextChanged("30")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(R.string.goals_hint_macroModeNeedsKcal, sut.macroModeHint)

        sut.onKcalTextChanged("2000")
        assertNull(sut.macroModeHint)
    }

    @Test
    fun macroModeHint_doesNotTurnIntoTheNeedsKcalMessageWhenTheLastMacroIsFilled() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onProteinTextChanged("20")
        sut.onCarbsTextChanged("50")
        sut.onFatTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(R.string.goals_hint_macroModeIncomplete, sut.macroModeHint)

        sut.onFatTextChanged("30")
        assertNull(sut.macroModeHint)
        assertEquals(0, sut.kcalFocusRequestCount.value)
    }

    @Test
    fun rejectedMacroModeSwitch_withIncompleteMacros_doesNotRequestKcalFocus() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onCarbsTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(R.string.goals_hint_macroModeIncomplete, sut.macroModeHint)
        assertEquals(0, sut.kcalFocusRequestCount.value)
    }

    @Test
    fun macroModeToGrams_withPercentsNotSummingTo100_keepsPercentModeAndShowsHint() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onProteinTextChanged("20")
        sut.onCarbsTextChanged("50")
        sut.onFatTextChanged("20")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.PERCENT, sut.macroMode.value)
        assertEquals("50", sut.carbsText.value)
        assertEquals(R.string.goals_hint_macroModeSumNot100, sut.macroModeHint)
        assertEquals(0, sut.kcalFocusRequestCount.value)
    }

    @Test
    fun macroModeHint_sumNot100_disappearsOnceThePercentsSumTo100() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onFatTextChanged("20")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.PERCENT, sut.macroMode.value)
        sut.onFatTextChanged("30")
        assertNull(sut.macroModeHint)
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.GRAMS, sut.macroMode.value)
    }

    @Test
    fun macroModeToGrams_withEmptyKcalAndPercentsNotSummingTo100_asksForKcalFirst() {
        val sut = makeSUT()
        sut.onKcalTextChanged("")
        sut.onFatTextChanged("20")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(R.string.goals_hint_macroModeNeedsKcal, sut.macroModeHint)
    }

    @Test
    fun kcalOfEnteredGramsDifferingFromGoal_gramsMatchingTheGoal_isNil() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onProteinTextChanged("100")
        sut.onCarbsTextChanged("200")
        sut.onFatTextChanged("100")
        assertEquals("2100", sut.kcalText.value)
        assertNull(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    @Test
    fun kcalOfEnteredGramsDifferingFromGoal_kcalGoalTypedDifferentFromGrams_isTheGramsKcal() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onProteinTextChanged("100")
        sut.onCarbsTextChanged("200")
        sut.onFatTextChanged("100")
        sut.onKcalTextChanged("2500")
        assertEquals(2100.0, sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    @Test
    fun kcalOfEnteredGramsDifferingFromGoal_afterSwitchingASplitToGrams_ignoresTheRoundingOfWholeGrams() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onProteinTextChanged("20")
        sut.onCarbsTextChanged("50")
        sut.onFatTextChanged("30")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.GRAMS, sut.macroMode.value)
        assertEquals(2003.0, sut.kcalOfEnteredGrams)
        assertNull(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    @Test
    fun kcalOfEnteredGramsDifferingFromGoal_justBeyondTheTolerance_isTheGramsKcal() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onProteinTextChanged("100")
        sut.onCarbsTextChanged("200")
        sut.onFatTextChanged("100")
        sut.onKcalTextChanged("2090")
        assertNull(sut.kcalOfEnteredGramsDifferingFromGoal)
        sut.onKcalTextChanged("2089")
        assertEquals(2100.0, sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    @Test
    fun kcalOfEnteredGramsDifferingFromGoal_withEmptyKcalGoal_isNil() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onKcalTextChanged("")
        assertNull(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    @Test
    fun kcalOfEnteredGramsDifferingFromGoal_inPercentMode_isNil() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2500")
        assertNull(sut.kcalOfEnteredGramsDifferingFromGoal)
    }

    @Test
    fun onEndConfirmed_whileSaving_isIgnored() = runTest {
        val save = SuspendedSaveDailyGoal()
        val sut = makeSUT(saveDailyGoal = save)
        sut.onKcalTextChanged("2100")
        val saving = launch { sut.onSaveTapped() }
        runCurrent()
        sut.onEndConfirmed()
        save.gate.complete(Unit)
        saving.join()
        assertEquals(1, save.goals.size)
        assertEquals(2100.0, save.goals.first().kcalGoal)
    }

    @Test
    fun onSaveTapped_cancelledWhileSaving_stillNotifiesChange() = runTest {
        val save = SuspendedSaveDailyGoal()
        val changes = mutableListOf<DailyGoalHistory>()
        val sut = makeSUT(saveDailyGoal = save, onChanged = { changes += it })
        sut.onKcalTextChanged("2100")
        val saving = launch { sut.onSaveTapped() }
        runCurrent()
        saving.cancel()
        save.gate.complete(Unit)
        saving.join()
        assertEquals(1, changes.size)
        assertEquals(2100.0, changes.first().entries.first().kcalGoal)
        assertFalse(sut.isSaving.value)
    }

    @Test
    fun macroModeToGrams_withOneEmptyMacro_keepsPercentModeAndTexts() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onCarbsTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.PERCENT, sut.macroMode.value)
        assertEquals("20", sut.proteinText.value)
        assertEquals("", sut.carbsText.value)
    }

    @Test
    fun macroModeToPercent_withOneEmptyMacro_keepsGramsModeAndTexts() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onFatTextChanged("")
        sut.onMacroModeChanged(GoalMacroMode.PERCENT)
        assertEquals(GoalMacroMode.GRAMS, sut.macroMode.value)
        assertEquals("100", sut.proteinText.value)
        assertEquals("", sut.fatText.value)
    }

    @Test
    fun macroModeToGrams_withKcal_convertsTexts() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        assertEquals(GoalMacroMode.GRAMS, sut.macroMode.value)
        assertEquals("100", sut.proteinText.value)
    }

    // MARK: - Validation

    @Test
    fun canSave_negativeMacroSummingTo100_isFalse() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onProteinTextChanged("120")
        sut.onCarbsTextChanged("-20")
        sut.onFatTextChanged("0")
        assertFalse(sut.isPercentSumInvalid)
        assertFalse(sut.canSave)
    }

    @Test
    fun canSave_gramsModeAboveLimit_isFalse() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2000")
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onProteinTextChanged("1500")
        sut.onCarbsTextChanged("0")
        sut.onFatTextChanged("0")
        assertTrue((sut.kcal ?: 0.0) in SaveDailyGoalUseCase.KCAL_RANGE)
        assertFalse(sut.canSave)
        sut.onProteinTextChanged("150")
        assertTrue(sut.canSave)
    }

    // MARK: - Collapsible sections

    @Test
    fun sections_withStoredBodyDataAndActivity_startCollapsedAndToggle() {
        val goal = DailyGoalDomain(
            "2026-11-01",
            kcalGoal = 2000.0,
            proteinPercent = 20.0,
            carbsPercent = 50.0,
            fatPercent = 30.0,
            sex = GoalSex.FEMALE,
            birthYear = 1990,
            heightCm = 165.0,
            weightKg = 60.0,
            activityLevel = GoalActivityLevel.LIGHT,
        )
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)))
        assertTrue(sut.isBodyDataRemembered)
        assertTrue(sut.isActivityRemembered)
        assertTrue(sut.isBodyDataCollapsed.value)
        assertTrue(sut.isActivityCollapsed.value)

        sut.onBodyDataToggled()
        sut.onActivityToggled()
        assertFalse(sut.isBodyDataCollapsed.value)
        assertFalse(sut.isActivityCollapsed.value)
    }

    @Test
    fun sections_withoutStoredValues_areNotCollapsible() {
        val sut = makeSUT()
        assertFalse(sut.isBodyDataRemembered)
        assertFalse(sut.isActivityRemembered)
        assertFalse(sut.isBodyDataCollapsed.value)
        assertFalse(sut.isActivityCollapsed.value)
    }

    @Test
    fun activity_withoutStoredValue_defaultsToLightWithoutCountingAsAChange() {
        val sut = makeSUT()
        assertEquals(GoalActivityLevel.LIGHT, sut.activityLevel.value)
        assertFalse("the default must not collapse the section", sut.isActivityRemembered)
        assertFalse(sut.canSave)
    }

    @Test
    fun sections_withPartialBodyData_keepBodyDataOpenButCollapseKnownActivity() {
        val goal = DailyGoalDomain("2026-11-01", kcalGoal = 2000.0, sex = GoalSex.MALE, activityLevel = GoalActivityLevel.HIGH)
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)))
        assertFalse(sut.isBodyDataCollapsed.value)
        assertTrue(sut.isActivityCollapsed.value)
    }

    @Test
    fun sexChange_inGramsMode_rescalesGramsToTheNewReferenceKcal() {
        val sut = makeSUT()
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onSexChanged(GoalSex.FEMALE)
        assertEquals("1900", sut.kcalText.value)
        assertEquals(1900.0, checkNotNull(sut.kcalOfEnteredGrams), 10.0)
    }

    @Test
    fun variantSliderReleased_withoutMatchingVariant_appliesTheVariantUnderTheThumb() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        val variants = checkNotNull(sut.estimate.value).variants
        sut.onKcalTextChanged("1234")
        sut.onVariantSliderReleased()
        val index = sut.selectedVariantIndex.value.toInt()
        assertEquals(variants[index].kcal.toDouble(), sut.kcal)
        assertEquals(index, sut.matchingVariantIndex)
    }

    @Test
    fun variantSliderReleased_withMatchingVariant_keepsKcal() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        sut.onVariantSelected(0)
        val kcal = sut.kcal
        sut.onVariantSliderReleased()
        assertEquals(kcal, sut.kcal)
    }

    // MARK: - Variants

    @Test
    fun variantSelected_writesTheVariantKcalAtOnceAndOnlyThenMatches() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        val variants = checkNotNull(sut.estimate.value).variants
        assertNull("a goal typed by hand matches no variant", sut.matchingVariantIndex)

        sut.onVariantSelected(0)
        assertEquals(variants[0].kcal.toDouble(), sut.kcal)
        assertEquals(0, sut.matchingVariantIndex)
        assertTrue(variants[0].kcal < variants[2].kcal)

        sut.onKcalTextChanged("1234")
        assertNull(sut.matchingVariantIndex)
    }

    @Test
    fun variantSelected_inGramsMode_keepsTheMacroSplitAndRescalesTheGrams() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(gramsProfileGoal(150.0, 250.0, 67.0))))
        val variants = checkNotNull(sut.estimate.value).variants
        val before = checkNotNull(sut.protein)

        sut.onVariantSelected(4)
        assertEquals(variants[4].kcal.toDouble(), sut.kcal)
        assertTrue(checkNotNull(sut.protein) > before)
        assertEquals(variants[4].kcal.toDouble(), checkNotNull(sut.kcalOfEnteredGrams), 30.0)
    }

    @Test
    fun variantSelected_inGramsMode_returningToAVariantRestoresTheSameGrams() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(gramsProfileGoal(10.0, 20.0, 3.0))))

        sut.onVariantSelected(2)
        val expected = listOf(sut.proteinText.value, sut.carbsText.value, sut.fatText.value)
        sut.onVariantSelected(0)
        sut.onVariantSelected(4)
        sut.onVariantSelected(1)
        sut.onVariantSelected(2)
        assertEquals(expected, listOf(sut.proteinText.value, sut.carbsText.value, sut.fatText.value))
    }

    @Test
    fun variantSelected_inGramsMode_afterEditingAMacro_rescalesFromTheEditedSplit() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(gramsProfileGoal(10.0, 20.0, 3.0))))

        sut.onVariantSelected(0)
        sut.onProteinTextChanged("400")
        val carbs = checkNotNull(sut.carbs)
        val fat = checkNotNull(sut.fat)
        val expectedShare = 1600 / (1600 + carbs * 4 + fat * 9)
        sut.onVariantSelected(4)
        val protein = checkNotNull(sut.protein) * 4
        val total = checkNotNull(sut.kcalOfEnteredGrams)
        assertEquals(expectedShare, protein / total, 0.02)
    }

    @Test
    fun activityChange_reappliesTheVariantUnderTheSlider() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        sut.onVariantSelected(3)
        val before = checkNotNull(sut.kcal)

        sut.onActivityLevelChanged(GoalActivityLevel.HIGH)
        val variants = checkNotNull(sut.estimate.value).variants
        assertEquals(variants[3].kcal.toDouble(), sut.kcal)
        assertTrue(checkNotNull(sut.kcal) > before)
        assertEquals(3, sut.matchingVariantIndex)
    }

    @Test
    fun activityChange_afterReopeningAGoalSavedAtAVariant_keepsThatVariant() {
        var goal = profileGoal(GoalMacroMode.PERCENT)
        val probe = makeSUT(history = DailyGoalHistory(listOf(goal)))
        goal = goal.copy(kcalGoal = checkNotNull(probe.estimate.value).variants[3].kcal.toDouble())
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)))
        assertEquals(3, sut.matchingVariantIndex)

        sut.onActivityLevelChanged(GoalActivityLevel.HIGH)
        assertEquals(3, sut.matchingVariantIndex)
    }

    @Test
    fun activityChange_afterTypingAKcalThatMatchesAVariant_appliesThatVariant() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        sut.onVariantSelected(4)
        sut.onKcalTextChanged(checkNotNull(sut.estimate.value).variants[1].kcal.toString())
        assertEquals(1, sut.matchingVariantIndex)

        sut.onActivityLevelChanged(GoalActivityLevel.HIGH)
        assertEquals(1, sut.matchingVariantIndex)
    }

    @Test
    fun step_inGramsMode_afterReopeningAGoalSavedAtAVariant_keepsTheSliderOnThatVariant() {
        var goal = gramsProfileGoal(150.0, 250.0, 67.0)
        val probe = makeSUT(history = DailyGoalHistory(listOf(goal)))
        goal = goal.copy(kcalGoal = checkNotNull(probe.estimate.value).variants[3].kcal.toDouble())
        val sut = makeSUT(history = DailyGoalHistory(listOf(goal)))
        assertEquals(3, sut.matchingVariantIndex)

        sut.onStep(GoalMacro.PROTEIN, 1)
        assertEquals(null, sut.matchingVariantIndex)
        assertEquals(3.0, sut.selectedVariantIndex.value, 0.0)
    }

    @Test
    fun activityChange_inGramsMode_scalesTheGramsWithTheNewKcal() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(gramsProfileGoal(150.0, 250.0, 67.0))))
        sut.onVariantSelected(3)
        val proteinBefore = checkNotNull(sut.protein)

        sut.onActivityLevelChanged(GoalActivityLevel.HIGH)
        val variants = checkNotNull(sut.estimate.value).variants
        assertEquals(variants[3].kcal.toDouble(), sut.kcal)
        assertTrue(checkNotNull(sut.protein) > proteinBefore)
        assertEquals(variants[3].kcal.toDouble(), checkNotNull(sut.kcalOfEnteredGrams), 30.0)
    }

    @Test
    fun activityChange_withoutEstimate_keepsTheKcalGoal() {
        val sut = makeSUT()
        sut.onKcalTextChanged("2345")
        sut.onActivityLevelChanged(GoalActivityLevel.HIGH)
        assertEquals("2345", sut.kcalText.value)
    }

    @Test
    fun sexChange_reappliesTheVariantUnderTheSliderToTheNewEstimate() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        sut.onVariantSelected(3)
        val before = checkNotNull(sut.kcal)

        sut.onSexChanged(GoalSex.FEMALE)
        val variants = checkNotNull(sut.estimate.value).variants
        assertEquals(variants[3].kcal.toDouble(), sut.kcal)
        assertTrue(checkNotNull(sut.kcal) < before)
        assertEquals(3, sut.matchingVariantIndex)
    }

    @Test
    fun sexChange_overwritesAKcalTypedByHand() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
        sut.onKcalTextChanged("1234")

        sut.onSexChanged(GoalSex.FEMALE)
        assertNotNull(sut.matchingVariantIndex)
        assertNotEquals("1234", sut.kcalText.value)
    }

    @Test
    fun sexChange_withoutEstimate_overwritesATypedKcalWithTheReference() {
        val sut = makeSUT()
        sut.onKcalTextChanged("1900")
        sut.onSexChanged(GoalSex.FEMALE)
        sut.onKcalTextChanged("1900")
        sut.onSexChanged(GoalSex.MALE)
        assertEquals("2400", sut.kcalText.value)
    }

    @Test
    fun sexChange_inGramsModeWithEstimate_scalesTheGramsWithTheNewKcal() {
        val sut = makeSUT(history = DailyGoalHistory(listOf(gramsProfileGoal(150.0, 250.0, 67.0))))
        sut.onVariantSelected(3)
        val proteinBefore = checkNotNull(sut.protein)

        sut.onSexChanged(GoalSex.FEMALE)
        val variants = checkNotNull(sut.estimate.value).variants
        assertEquals(variants[3].kcal.toDouble(), sut.kcal)
        assertTrue(checkNotNull(sut.protein) < proteinBefore)
        assertEquals(variants[3].kcal.toDouble(), checkNotNull(sut.kcalOfEnteredGrams), 30.0)
    }

    @Test
    fun macroModeSwitch_withNonPositiveOrNonFiniteMacro_isRejected() {
        for (invalid in listOf("-10", "NaN", "Infinity")) {
            val sut = makeSUT(history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT))))
            sut.onProteinTextChanged(invalid)
            sut.onCarbsTextChanged("60")
            sut.onFatTextChanged("50")
            sut.onMacroModeChanged(GoalMacroMode.GRAMS)
            assertEquals("$invalid must not pass as a macro", GoalMacroMode.PERCENT, sut.macroMode.value)
            assertEquals(GoalMacroMode.GRAMS, sut.rejectedMacroMode.value)
        }
    }

    @Test
    fun init_afterAnEndedGoalWithAProfile_prefillsTheMaintainingEstimateInsteadOfTheReference() {
        val history = DailyGoalHistory(listOf(profileGoal(GoalMacroMode.PERCENT), DailyGoalDomain.ended("2026-11-05")))
        val sut = makeSUT(history = history)
        assertFalse(sut.hasActiveGoal)
        val variants = checkNotNull(sut.estimate.value).variants
        val maintaining = GoalEstimate.percentChanges.indexOf(0)
        assertEquals(variants[maintaining].kcal.toDouble(), sut.kcal)
        assertEquals(maintaining, sut.matchingVariantIndex)
        assertNotEquals("2400", sut.kcalText.value)
    }

    @Test
    fun bodyDataCompleted_withTheReferencePrefill_appliesTheVariantUnderTheSlider() {
        val sut = makeSUT()
        assertEquals("2400", sut.kcalText.value)
        sut.onBirthYearChanged(1990)
        sut.onHeightChanged(180)
        assertEquals("2400", sut.kcalText.value)
        sut.onWeightChanged(80.0)

        val variants = checkNotNull(sut.estimate.value).variants
        val index = sut.selectedVariantIndex.value.toInt()
        assertEquals(variants[index].kcal.toDouble(), sut.kcal)
        assertEquals(index, sut.matchingVariantIndex)
    }

    @Test
    fun bodyDataCompleted_afterTypingKcal_keepsTheTypedKcal() {
        val sut = makeSUT()
        sut.onKcalTextChanged("1999")
        sut.onBirthYearChanged(1990)
        sut.onHeightChanged(180)
        sut.onWeightChanged(80.0)
        assertEquals("1999", sut.kcalText.value)
    }

    @Test
    fun bodyDataCompleted_inGramsMode_scalesTheGramsWithTheNewKcal() {
        val sut = makeSUT()
        sut.onMacroModeChanged(GoalMacroMode.GRAMS)
        sut.onBirthYearChanged(1990)
        sut.onHeightChanged(180)
        sut.onWeightChanged(80.0)
        val kcal = checkNotNull(sut.kcal)
        assertEquals(kcal, checkNotNull(sut.kcalOfEnteredGrams), 30.0)
    }

    @Test
    fun init_withoutAnyProfile_prefillsTheReference() {
        assertEquals("2400", makeSUT().kcalText.value)
    }

    // MARK: - Helpers

    private fun percentGoalWithoutProfile() = DailyGoalDomain(
        "2026-11-01",
        kcalGoal = 2000.0,
        proteinPercent = 20.0,
        carbsPercent = 50.0,
        fatPercent = 30.0,
    )

    private fun profileGoal(macroMode: GoalMacroMode): DailyGoalDomain {
        val goal = DailyGoalDomain(
            "2026-11-01",
            kcalGoal = 2000.0,
            macroMode = macroMode,
            sex = GoalSex.MALE,
            birthYear = 1990,
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = GoalActivityLevel.MODERATE,
        )
        return if (macroMode == GoalMacroMode.PERCENT) goal.copy(proteinPercent = 20.0, carbsPercent = 50.0, fatPercent = 30.0) else goal
    }

    private fun gramsProfileGoal(protein: Double, carbs: Double, fat: Double) = profileGoal(GoalMacroMode.GRAMS).copy(proteinGrams = protein, carbsGrams = carbs, fatGrams = fat)

    private fun makeSUT(
        history: DailyGoalHistory = DailyGoalHistory.empty,
        unit: BodyWeightUnit = BodyWeightUnit.KG,
        fetchBodyWeights: FetchBodyWeightsUseCaseProtocol = FetchBodyWeightsUseCaseFake(),
        saveBodyWeight: SaveBodyWeightUseCaseProtocol = SaveBodyWeightUseCaseFake(),
        saveDailyGoal: SaveDailyGoalUseCaseProtocol = SaveDailyGoalUseCaseFake(),
        onChanged: (DailyGoalHistory) -> Unit = {},
    ): DailyGoalsViewModel = DailyGoalsViewModel(
        history = history,
        unit = unit,
        fetchBodyWeights = fetchBodyWeights,
        saveDailyGoal = saveDailyGoal,
        saveBodyWeight = saveBodyWeight,
        onChanged = onChanged,
    )
}

private class SuspendedSaveDailyGoal : SaveDailyGoalUseCaseProtocol {
    val goals = mutableListOf<DailyGoalDomain>()
    val gate = CompletableDeferred<Unit>()

    override suspend fun invoke(goal: DailyGoalDomain): DailyGoalHistory {
        goals += goal
        if (goals.size == 1) gate.await()
        return DailyGoalHistory(listOf(goal))
    }
}

private class SaveBodyWeightRecorder : SaveBodyWeightUseCaseProtocol {
    val loggedKilograms = mutableListOf<Double>()

    override suspend fun invoke(kilograms: Double, day: Instant) {
        loggedKilograms += kilograms
    }
}

private class SuspendedFetchBodyWeights(private val weights: List<BodyWeightDomain>) : FetchBodyWeightsUseCaseProtocol {
    val gate = CompletableDeferred<Unit>()

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        gate.await()
        return weights
    }

    override suspend fun after(day: Instant): List<BodyWeightDomain> = emptyList()
    override suspend fun between(from: Instant, to: Instant): List<BodyWeightDomain> = emptyList()
    override suspend fun inYear(year: Int): List<BodyWeightDomain> = emptyList()
}
