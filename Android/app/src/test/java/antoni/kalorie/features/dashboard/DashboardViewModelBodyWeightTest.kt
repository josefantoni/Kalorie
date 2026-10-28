package antoni.kalorie.features.dashboard

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseFake
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseFake
import antoni.kalorie.core.usecases.CopyFoodsConsumedUseCaseFake
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseFake
import antoni.kalorie.core.usecases.FetchAppSettingsUseCaseFake
import antoni.kalorie.core.usecases.FetchAppSettingsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseFake
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseFake
import antoni.kalorie.core.usecases.FetchFoodsConsumedInRangeUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseFake
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseFake
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseProtocol
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseFake
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DashboardViewModelBodyWeightTest {

    // MARK: - Tests

    @Test
    fun weightCircle_isHiddenUntilSettingsLoaded_thenShownByDefault() = runTest {
        val sut = makeSUT()
        assertFalse(sut.isWeightCircleVisible)
        sut.onAppear()
        assertTrue(sut.isWeightCircleVisible)
    }

    @Test
    fun weightCircle_staysHiddenWhenTheSettingsReadFails_soADisabledCircleNeverFlashesBack() = runTest {
        val sut = makeSUT(fetchAppSettings = FetchAppSettingsUseCaseFake(shouldThrow = true))
        sut.onAppear()
        assertFalse(sut.isWeightCircleVisible)
    }

    @Test
    fun weightCircle_isHiddenWhenSettingsTurnItOff() = runTest {
        val settings = AppSettingsDomain(showBodyWeight = false, bodyWeightUnit = BodyWeightUnit.KG)
        val sut = makeSUT(fetchAppSettings = FetchAppSettingsUseCaseFake(stubbedSettings = settings))
        sut.onAppear()
        assertFalse(sut.isWeightCircleVisible)
    }

    @Test
    fun weights_cachedPerDay_secondVisitDoesNotReload() = runTest {
        val fetch = CountingFetchBodyWeights()
        val sut = makeSUT(fetchBodyWeights = fetch)
        sut.onAppear()
        val yesterday = Instant.now().minusSeconds(DAY)
        sut.onDaySelected(yesterday)
        sut.onDaySelected(Instant.now())
        sut.onDaySelected(yesterday)
        assertEquals(2, fetch.callCount)
    }

    @Test
    fun weights_laterEntriesAreOnlyQueriedForDaysBeforeToday() = runTest {
        val fetch = CountingFetchBodyWeights()
        val sut = makeSUT(fetchBodyWeights = fetch)
        sut.onAppear()
        assertEquals("nothing can be logged after today, so the query would only cost a read", 0, fetch.afterCallCount)
        sut.onDaySelected(Instant.now().minusSeconds(3 * DAY))
        assertEquals(1, fetch.afterCallCount)
    }

    @Test
    fun weights_laterEntriesAreExposedForAPastDay() = runTest {
        val later = listOf(BodyWeightDomain(dayKey = "2026-10-28", kilograms = 77.9))
        val sut = makeSUT(fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedLaterWeights = later))
        sut.onAppear()
        sut.onDaySelected(Instant.now().minusSeconds(3 * DAY))
        assertEquals(later, sut.laterWeights.value)
    }

    @Test
    fun weights_afterWrite_cacheIsClearedAndSelectedDayReloaded() = runTest {
        val fetch = CountingFetchBodyWeights()
        val sut = makeSUT(fetchBodyWeights = fetch)
        sut.onAppear()
        sut.onWeightsChanged()
        assertEquals(2, fetch.callCount)
    }

    @Test
    fun weights_loadFailure_doesNotShowThePreviousDaysWeight() = runTest {
        val todayEntry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(Instant.now()), kilograms = 78.4)
        val sut = makeSUT(fetchBodyWeights = FailingAfterFirstFetchBodyWeights(firstResult = listOf(todayEntry)))
        sut.onAppear()
        assertEquals(listOf(todayEntry), sut.recentWeights.value)
        sut.onDaySelected(Instant.now().minusSeconds(DAY))
        assertTrue(sut.recentWeights.value.isEmpty())
        assertTrue(sut.laterWeights.value.isEmpty())
        assertFalse(sut.hasWeightForSelectedDay)
    }

    @Test
    fun weights_failedLoadIsNotCached_soTheNextForegroundLoadsThemAgain() = runTest {
        val todayEntry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(Instant.now()), kilograms = 78.4)
        val sut = makeSUT(fetchBodyWeights = FailingFirstFetchBodyWeights(laterResult = listOf(todayEntry)))
        sut.onAppear()
        assertFalse(sut.canLogWeight)
        sut.onForeground()
        assertEquals("a failed read must not be remembered as an empty window", listOf(todayEntry), sut.recentWeights.value)
        assertTrue(sut.canLogWeight)
    }

    @Test
    fun weights_failedReloadAfterAWrite_hidesTheStaleWindow_soTheBadgeDoesNotOfferToOverwriteIt() = runTest {
        val olderEntry = BodyWeightDomain(dayKey = "2026-10-01", kilograms = 81.3)
        val sut = makeSUT(fetchBodyWeights = FailingAfterFirstFetchBodyWeights(firstResult = listOf(olderEntry)))
        sut.onAppear()
        assertTrue(sut.canLogWeight)
        sut.onWeightsChanged()
        assertTrue(sut.recentWeights.value.isEmpty())
        assertTrue(sut.laterWeights.value.isEmpty())
        assertFalse("the window predates the write or delete, so seeding the picker from it could overwrite a real entry", sut.canLogWeight)
    }

    @Test
    fun weights_failedRefresh_keepsTheWindowItDidNotInvalidateByAWrite() = runTest {
        val todayEntry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(Instant.now()), kilograms = 78.4)
        val sut = makeSUT(fetchBodyWeights = FailingAfterFirstFetchBodyWeights(firstResult = listOf(todayEntry)))
        sut.onAppear()
        sut.onRefresh()
        assertEquals(listOf(todayEntry), sut.recentWeights.value)
        assertTrue(sut.canLogWeight)
    }

    @Test
    fun weightBadge_today_isAvailableWhetherOrNotAWeightIsLogged() = runTest {
        val sut = makeSUT()
        sut.onAppear()
        assertTrue(sut.canLogWeight)
    }

    @Test
    fun weightBadge_whenTheWeightsCouldNotBeLoaded_isUnavailable_soARealEntryIsNotOverwrittenWithTheDefault() = runTest {
        val sut = makeSUT(fetchBodyWeights = FetchBodyWeightsUseCaseFake(shouldThrow = true))
        sut.onAppear()
        assertFalse(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        assertFalse(sut.isWeightPopoverVisible.value)
    }

    @Test
    fun weightBadge_pastDayWithoutEntry_isAvailable_soAMissedDayCanBeBackfilled() = runTest {
        val earlierEntry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(Instant.now().minusSeconds(10 * DAY)), kilograms = 79.5)
        val sut = makeSUT(fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(earlierEntry)))
        sut.onAppear()
        sut.onDaySelected(Instant.now().minusSeconds(3 * DAY))
        assertFalse(sut.hasWeightForSelectedDay)
        assertTrue(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        assertTrue(sut.isWeightPopoverVisible.value)
        assertEquals("the picker starts from the weight valid on that day", 79.5, sut.weightPickerKilograms.value, 0.0)
    }

    @Test
    fun weightBadge_pastDayWithEntry_staysAvailableToEditThatEntry() = runTest {
        val day = Instant.now().minusSeconds(3 * DAY)
        val entry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(day), kilograms = 80.0)
        val sut = makeSUT(fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(entry)))
        sut.onAppear()
        sut.onDaySelected(day)
        assertTrue(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        assertTrue(sut.isWeightPopoverVisible.value)
        assertEquals(80.0, sut.weightPickerKilograms.value, 0.0)
    }

    @Test
    fun weightBadge_futureDay_isNotAvailableAndDoesNotOpenPicker() = runTest {
        val sut = makeSUT()
        sut.onAppear()
        sut.onDaySelected(Instant.now().plusSeconds(3 * DAY))
        assertFalse(sut.canLogWeight)
        sut.onWeightBadgeTapped()
        assertFalse(sut.isWeightPopoverVisible.value)
    }

    @Test
    fun weightPicker_initialValue_isWeightValidOnDayElseDefault() = runTest {
        val withWeight = makeSUT(
            fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(BodyWeightDomain(dayKey = "2026-10-01", kilograms = 81.3))),
        )
        withWeight.onAppear()
        withWeight.onWeightBadgeTapped()
        assertEquals(81.3, withWeight.weightPickerKilograms.value, 0.0)

        val empty = makeSUT()
        empty.onAppear()
        empty.onWeightBadgeTapped()
        assertEquals(70.0, empty.weightPickerKilograms.value, 0.0)
    }

    @Test
    fun weightSave_failure_keepsPickerOpenAndAlerts() = runTest {
        val sut = makeSUT(saveBodyWeight = SaveBodyWeightUseCaseFake(shouldThrow = true))
        sut.onAppear()
        sut.onWeightBadgeTapped()
        sut.onWeightConfirmed()
        assertTrue(sut.isWeightPopoverVisible.value)
        assertNotNull(sut.alertItem.value)
    }

    @Test
    fun weightSave_writesToTheDayThePickerWasOpenedOn_evenWhenTheSelectedDayMovesMeanwhile() = runTest {
        val save = RecordingSaveBodyWeight()
        val sut = makeSUT(saveBodyWeight = save)
        sut.onAppear()
        val opened = sut.selectedDay.value
        sut.onWeightBadgeTapped()
        sut.onDaySelected(opened.minusSeconds(DAY))
        sut.onWeightConfirmed()
        assertEquals(listOf(opened), save.days)
    }

    @Test
    fun weights_stillLoad_whenMealTypesFailAtLaunch_soTheBadgeDoesNotOfferToOverwriteARealEntry() = runTest {
        val entry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(Instant.now()), kilograms = 78.4)
        val sut = makeSUT(
            fetchBodyWeights = FetchBodyWeightsUseCaseFake(stubbedWeights = listOf(entry)),
            fetchMealTypes = FetchMealTypesUseCaseFake(shouldThrow = true),
        )
        sut.onAppear()
        assertEquals(listOf(entry), sut.recentWeights.value)
        assertTrue(sut.hasWeightForSelectedDay)
        assertTrue(sut.isWeightCircleVisible)
    }

    @Test
    fun weights_cachedWindowOfAnotherDay_isNotShownOnTheSelectedDay() = runTest {
        val today = Instant.now()
        val yesterday = today.minusSeconds(DAY)
        val sut = makeSUT(fetchBodyWeights = PerDayFetchBodyWeights())
        sut.onAppear()
        sut.onDaySelected(yesterday)
        sut.onDaySelected(today)
        sut.onDayChanged(yesterday)
        assertEquals(BodyWeightDayKey.make(today), sut.recentWeights.value.firstOrNull()?.dayKey)
    }

    @Test
    fun weights_whileANewDayLoads_thePreviousDaysWindowIsNotShownAndTheBadgeIsUnavailable() = runTest {
        val today = Instant.now()
        val todayEntry = BodyWeightDomain(dayKey = BodyWeightDayKey.make(today), kilograms = 78.4)
        val fetch = GatedSecondFetchBodyWeights(firstResult = listOf(todayEntry))
        val sut = makeSUT(fetchBodyWeights = fetch)
        sut.onAppear()
        assertTrue(sut.canLogWeight)
        val selection = launch { sut.onDaySelected(today.minusSeconds(DAY)) }
        runCurrent()
        assertTrue("yesterday must not be drawn with today's weight", sut.recentWeights.value.isEmpty())
        assertFalse(sut.hasWeightForSelectedDay)
        assertFalse("the picker must not be seeded from a window that belongs to another day", sut.canLogWeight)
        fetch.gate.complete(Unit)
        selection.join()
    }

    @Test
    fun weights_staleLoadFinishingAfterAWrite_doesNotOverwriteTheFreshWindow() = runTest {
        val saved = BodyWeightDomain(dayKey = BodyWeightDayKey.make(Instant.now()), kilograms = 79.0)
        val fetch = GatedFirstFetchBodyWeights(freshResult = listOf(saved))
        val sut = makeSUT(fetchBodyWeights = fetch)
        val launch = launch { sut.onAppear() }
        runCurrent()
        sut.onWeightsChanged()
        assertEquals(listOf(saved), sut.recentWeights.value)
        fetch.gate.complete(Unit)
        launch.join()
        assertEquals("a load that started before the write must not replace the window read after it", listOf(saved), sut.recentWeights.value)
    }

    @Test
    fun settings_staleReadFinishingAfterAChange_doesNotOverwriteTheNewerSetting() = runTest {
        val stale = AppSettingsDomain(showBodyWeight = true, bodyWeightUnit = BodyWeightUnit.KG)
        val changed = AppSettingsDomain(showBodyWeight = true, bodyWeightUnit = BodyWeightUnit.LB)
        val fetch = GatedFetchAppSettings(result = stale)
        val sut = makeSUT(fetchAppSettings = fetch)
        val launch = launch { sut.onAppear() }
        runCurrent()
        sut.onAppSettingsChanged(changed)
        fetch.gate.complete(Unit)
        launch.join()
        assertEquals("a read that started before the user changed a setting must not bring the old value back", changed, sut.appSettings.value)
    }

    // MARK: - Helpers

    private fun makeSUT(
        fetchBodyWeights: FetchBodyWeightsUseCaseProtocol = FetchBodyWeightsUseCaseFake(),
        fetchAppSettings: FetchAppSettingsUseCaseProtocol = FetchAppSettingsUseCaseFake(),
        fetchMealTypes: FetchMealTypesUseCaseProtocol = FetchMealTypesUseCaseFake(),
        saveBodyWeight: SaveBodyWeightUseCaseProtocol = SaveBodyWeightUseCaseFake(),
    ): DashboardViewModel = DashboardViewModel(
        fetchMealTypes = fetchMealTypes,
        fetchFoodsConsumedForMonth = FetchFoodsConsumedForMonthUseCaseFake(),
        fetchFoodsConsumedInRange = FetchFoodsConsumedInRangeUseCaseFake(),
        setupDefaultMeals = SetupDefaultMealsUseCaseFake(),
        confirmMealTypesEmpty = ConfirmMealTypesEmptyUseCaseFake(stubbedResult = true),
        deleteFoodConsumed = DeleteFoodConsumedUseCaseFake(),
        copyFoodsConsumed = CopyFoodsConsumedUseCaseFake(),
        assignFoodMealType = AssignFoodMealTypeUseCaseFake(),
        fetchBodyWeights = fetchBodyWeights,
        fetchAppSettings = fetchAppSettings,
        saveBodyWeight = saveBodyWeight,
        authProvider = AuthProviderFake(),
        signInSpotlightStore = SignInSpotlightStoreFake(),
    )

    private companion object {
        const val DAY = 86_400L
    }
}

private class RecordingSaveBodyWeight : SaveBodyWeightUseCaseProtocol {
    val days = mutableListOf<Instant>()

    override suspend fun invoke(kilograms: Double, day: Instant) {
        days += day
    }
}

private open class StubFetchBodyWeights : FetchBodyWeightsUseCaseProtocol {
    override suspend fun upTo(day: Instant): List<BodyWeightDomain> = emptyList()
    override suspend fun after(day: Instant): List<BodyWeightDomain> = emptyList()
    override suspend fun between(from: Instant, to: Instant): List<BodyWeightDomain> = emptyList()
    override suspend fun inYear(year: Int): List<BodyWeightDomain> = emptyList()
}

private class FailingFirstFetchBodyWeights(private val laterResult: List<BodyWeightDomain>) : StubFetchBodyWeights() {
    private var hasFailed = false

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        if (!hasFailed) {
            hasFailed = true
            throw RuntimeException("unknown")
        }
        return laterResult
    }
}

private class FailingAfterFirstFetchBodyWeights(private val firstResult: List<BodyWeightDomain>) : StubFetchBodyWeights() {
    private var hasServedFirstResult = false

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        if (hasServedFirstResult) throw RuntimeException("unknown")
        hasServedFirstResult = true
        return firstResult
    }
}

private class CountingFetchBodyWeights : StubFetchBodyWeights() {
    var callCount = 0
    var afterCallCount = 0

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        callCount += 1
        return emptyList()
    }

    override suspend fun after(day: Instant): List<BodyWeightDomain> {
        afterCallCount += 1
        return emptyList()
    }
}

private class GatedFirstFetchBodyWeights(private val freshResult: List<BodyWeightDomain>) : StubFetchBodyWeights() {
    val gate = CompletableDeferred<Unit>()
    private var callCount = 0

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        callCount += 1
        if (callCount != 1) return freshResult
        gate.await()
        return emptyList()
    }
}

private class GatedSecondFetchBodyWeights(private val firstResult: List<BodyWeightDomain>) : StubFetchBodyWeights() {
    val gate = CompletableDeferred<Unit>()
    private var callCount = 0

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        callCount += 1
        if (callCount != 2) return firstResult
        gate.await()
        return emptyList()
    }
}

private class PerDayFetchBodyWeights : StubFetchBodyWeights() {
    override suspend fun upTo(day: Instant): List<BodyWeightDomain> = listOf(BodyWeightDomain(dayKey = BodyWeightDayKey.make(day), kilograms = 80.0))
}

private class GatedFetchAppSettings(private val result: AppSettingsDomain) : FetchAppSettingsUseCaseProtocol {
    val gate = CompletableDeferred<Unit>()

    override suspend fun invoke(): AppSettingsDomain {
        gate.await()
        return result
    }
}
