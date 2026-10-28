package antoni.kalorie.features.dashboard

import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.usecases.DeleteBodyWeightUseCaseProtocol
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class BodyWeightHistoryViewModelTest {

    // MARK: - Tests

    @Test
    fun onAppear_loadsWeightsNewestFirst() = runTest {
        val (sut, _) = makeSUT(weights = listOf(weight("2026-10-27", 78.0), weight("2026-10-20", 79.0)))
        sut.onAppear()
        assertEquals(listOf("2026-10-27", "2026-10-20"), sut.weights.value.map { it.dayKey })
    }

    @Test
    fun chartPoints_onlyIncludeCurrentPeriod_andPagingMovesWindow() = runTest {
        val (sut, _) = makeSUT(
            weights = listOf(weight("2026-10-27", 78.0), weight("2026-10-05", 79.0), weight("2026-09-15", 80.0)),
            now = date("2026-10-27"),
        )
        sut.onAppear()
        assertEquals(listOf(79.0, 78.0), sut.chartPoints.value.map { it.kilograms })
        sut.onPreviousPeriod()
        assertEquals(listOf(80.0), sut.chartPoints.value.map { it.kilograms })
    }

    @Test
    fun onAppear_readsOnlyTheCurrentYear_soALongHistoryCostsNothingUntilItIsPagedTo() = runTest {
        val (sut, spy) = makeSUT(
            weights = listOf(weight("2026-10-27", 78.0), weight("2025-12-30", 80.0), weight("2024-05-01", 82.0)),
            now = date("2026-10-27"),
        )
        sut.onAppear()
        assertEquals(listOf(2026), spy.requestedYears)
        assertEquals(listOf("2026-10-27"), sut.weights.value.map { it.dayKey })
    }

    @Test
    fun paging_withinTheLoadedYear_readsNothingMore() = runTest {
        val (sut, spy) = makeSUT(weights = listOf(weight("2026-10-27", 78.0), weight("2026-09-15", 80.0)), now = date("2026-10-27"))
        sut.onAppear()
        sut.onPreviousPeriod()
        assertEquals(listOf(2026), spy.requestedYears)
    }

    @Test
    fun paging_acrossTheYearBoundary_readsThePreviousYearOnce() = runTest {
        val (sut, spy) = makeSUT(
            weights = listOf(weight("2027-01-10", 78.0), weight("2026-12-20", 80.0), weight("2026-11-02", 81.0), weight("2025-05-01", 82.0)),
            now = date("2027-01-15"),
        )
        sut.onAppear()
        sut.onPreviousPeriod()
        assertEquals(listOf(2027, 2026), spy.requestedYears)
        assertEquals(listOf(80.0), sut.chartPoints.value.map { it.kilograms })
        sut.onPreviousPeriod()
        assertEquals("November is in the year that is already loaded", listOf(2027, 2026), spy.requestedYears)
        assertEquals(listOf("2027-01-10", "2026-12-20", "2026-11-02"), sut.weights.value.map { it.dayKey })
    }

    @Test
    fun isLoadingEarlierYear_isOnlyTrueWhileAnEarlierYearIsBeingRead() = runTest {
        val (sut, _) = makeSUT(weights = listOf(weight("2027-01-10", 78.0), weight("2026-12-20", 80.0)), now = date("2027-01-15"))
        sut.onAppear()
        assertFalse(sut.isLoadingEarlierYear.value)
        sut.onPreviousPeriod()
        assertFalse("it must be reset after the read, whether it succeeded or not", sut.isLoadingEarlierYear.value)
    }

    @Test
    fun changingToYearRange_readsOnlyWhatTheCurrentPeriodNeeds() = runTest {
        val (sut, spy) = makeSUT(weights = listOf(weight("2026-10-27", 78.0), weight("2025-12-30", 80.0)), now = date("2026-10-27"))
        sut.onAppear()
        sut.onRangeChanged(BodyWeightRange.YEAR)
        sut.loadEarlierYearsIfNeeded()
        assertEquals(listOf(2026), spy.requestedYears)
    }

    @Test
    fun failedEarlierYear_alertsAndIsRetriedByTheNextRequest() = runTest {
        val (sut, spy) = makeSUT(weights = listOf(weight("2027-01-10", 78.0), weight("2026-12-20", 80.0)), now = date("2027-01-15"))
        sut.onAppear()
        spy.failingYears = setOf(2026)
        sut.onPreviousPeriod()
        assertNotNull(sut.alertItem.value)
        spy.failingYears = emptySet()
        sut.loadEarlierYearsIfNeeded()
        assertEquals(listOf("2027-01-10", "2026-12-20"), sut.weights.value.map { it.dayKey })
    }

    @Test
    fun paging_cannotGoPastCurrentPeriod() = runTest {
        val (sut, _) = makeSUT(weights = emptyList())
        assertFalse(sut.canPageForward)
        sut.onNextPeriod()
        assertEquals(0, sut.periodOffset.value)
    }

    @Test
    fun paging_stopsAtTheFirstYear_andReadsNothingBeforeIt() = runTest {
        val (sut, spy) = makeSUT(weights = listOf(weight("2026-01-10", 78.0), weight("2025-12-20", 80.0)), now = date("2026-01-15"))
        sut.onAppear()
        assertFalse(sut.canPageBack)
        sut.onPreviousPeriod()
        assertEquals(0, sut.periodOffset.value)
        assertEquals(listOf(2026), spy.requestedYears)
    }

    @Test
    fun paging_inTheFirstYear_canGoBackUntilItsFirstMonth() = runTest {
        val (sut, _) = makeSUT(weights = listOf(weight("2026-03-10", 78.0)), now = date("2026-03-15"))
        sut.onAppear()
        sut.onPreviousPeriod()
        sut.onPreviousPeriod()
        assertEquals(-2, sut.periodOffset.value)
        assertFalse(sut.canPageBack)
        sut.onPreviousPeriod()
        assertEquals(-2, sut.periodOffset.value)
    }

    @Test
    fun changingRange_resetsPaging() = runTest {
        val (sut, _) = makeSUT(weights = emptyList())
        sut.onPreviousPeriod()
        sut.onRangeChanged(BodyWeightRange.YEAR)
        assertEquals(0, sut.periodOffset.value)
    }

    @Test
    fun delete_removesEntry_andReportsChangeOnDisappear() = runTest {
        var changedCount = 0
        val (sut, spy) = makeSUT(weights = listOf(weight("2026-10-27", 78.0)), onChanged = { changedCount += 1 })
        sut.onAppear()
        sut.onDeleteRequested(sut.weights.value[0])
        sut.onDeleteConfirmed()
        assertEquals(listOf("2026-10-27"), spy.deletedDayKeys)
        assertTrue(sut.weights.value.isEmpty())
        sut.onDisappear()
        sut.onDisappear()
        assertEquals(1, changedCount)
    }

    @Test
    fun disappear_withoutChanges_doesNotNotifyDashboard() = runTest {
        var changedCount = 0
        val (sut, _) = makeSUT(weights = listOf(weight("2026-10-27", 78.0)), onChanged = { changedCount += 1 })
        sut.onAppear()
        sut.onDisappear()
        assertEquals(0, changedCount)
    }

    @Test
    fun deleteFailure_keepsEntryAndAlerts() = runTest {
        val (sut, _) = makeSUT(weights = listOf(weight("2026-10-27", 78.0)), deleteShouldThrow = true)
        sut.onAppear()
        sut.onDeleteRequested(sut.weights.value[0])
        sut.onDeleteConfirmed()
        assertEquals(1, sut.weights.value.size)
        assertNotNull(sut.alertItem.value)
    }

    // MARK: - Helpers

    private fun weight(key: String, kilograms: Double) = BodyWeightDomain(dayKey = key, kilograms = kilograms)

    private fun date(key: String): Instant = checkNotNull(BodyWeightDayKey.date(key, ZONE))

    private fun makeSUT(
        weights: List<BodyWeightDomain>,
        now: Instant = Instant.now(),
        deleteShouldThrow: Boolean = false,
        onChanged: () -> Unit = {},
    ): Pair<BodyWeightHistoryViewModel, HistorySpy> {
        val spy = HistorySpy(weights, deleteShouldThrow)
        val sut = BodyWeightHistoryViewModel(
            unit = BodyWeightUnit.KG,
            fetchBodyWeights = spy,
            deleteBodyWeight = spy,
            now = { now },
            onChanged = onChanged,
            zone = ZONE,
            locale = Locale.ENGLISH,
        )
        return sut to spy
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    }
}

private class HistorySpy(
    private val weights: List<BodyWeightDomain>,
    private val deleteShouldThrow: Boolean,
) : FetchBodyWeightsUseCaseProtocol,
    DeleteBodyWeightUseCaseProtocol {
    val deletedDayKeys = mutableListOf<String>()
    val requestedYears = mutableListOf<Int>()
    var failingYears: Set<Int> = emptySet()

    override suspend fun inYear(year: Int): List<BodyWeightDomain> {
        requestedYears += year
        if (year in failingYears) throw RuntimeException("unknown")
        return weights.filter { it.dayKey.startsWith("%04d-".format(year)) }
    }

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> = weights

    override suspend fun after(day: Instant): List<BodyWeightDomain> = emptyList()

    override suspend fun between(from: Instant, to: Instant): List<BodyWeightDomain> = weights

    override suspend fun invoke(dayKey: String) {
        if (deleteShouldThrow) throw RuntimeException("unknown")
        deletedDayKeys += dayKey
    }
}
