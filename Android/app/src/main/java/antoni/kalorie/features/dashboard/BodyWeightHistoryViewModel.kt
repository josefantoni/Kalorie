package antoni.kalorie.features.dashboard

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.usecases.DeleteBodyWeightUseCaseProtocol
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class BodyWeightRange {
    WEEK,
    MONTH,
    YEAR,
}

data class BodyWeightChartPoint(
    val date: Instant,
    val kilograms: Double,
)

data class BodyWeightPeriod(
    val start: Instant,
    val end: Instant,
) {
    val durationSeconds: Long get() = end.epochSecond - start.epochSecond

    fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

class BodyWeightHistoryViewModel(
    val unit: BodyWeightUnit,
    private val selectedDay: Instant,
    private val fetchBodyWeights: FetchBodyWeightsUseCaseProtocol,
    private val deleteBodyWeight: DeleteBodyWeightUseCaseProtocol,
    private val now: () -> Instant = { Instant.now() },
    private val onChanged: () -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Loading)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _weights = MutableStateFlow<List<BodyWeightDomain>>(emptyList())
    val weights: StateFlow<List<BodyWeightDomain>> = _weights
    private val _periodOffset = MutableStateFlow(0)
    val periodOffset: StateFlow<Int> = _periodOffset
    private val _period = MutableStateFlow(BodyWeightPeriod(Instant.MIN, Instant.MIN))
    val period: StateFlow<BodyWeightPeriod> = _period
    private val _chartPoints = MutableStateFlow<List<BodyWeightChartPoint>>(emptyList())
    val chartPoints: StateFlow<List<BodyWeightChartPoint>> = _chartPoints
    private val _range = MutableStateFlow(BodyWeightRange.MONTH)
    val range: StateFlow<BodyWeightRange> = _range
    private val _isLoadingEarlierYear = MutableStateFlow(false)
    val isLoadingEarlierYear: StateFlow<Boolean> = _isLoadingEarlierYear
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val isDeleteConfirmationVisible = MutableStateFlow(false)

    private var pendingDeletion: BodyWeightDomain? = null
    private var hasChanges = false
    private var oldestLoadedYear: Int? = null

    val canPageForward: Boolean
        get() = _periodOffset.value < 0

    val canPageBack: Boolean
        get() = BodyWeightDayKey.make(_period.value.start, zone) > "%04d-01-01".format(Locale.ROOT, Constants.BodyWeight.FIRST_YEAR)

    // MARK: - Init

    init {
        _periodOffset.value = offsetOfSelectedDay(_range.value)
        refreshChart()
    }

    // MARK: - Functions

    suspend fun onAppear() {
        val currentYear = BodyWeightDayKey.year(now(), zone)
        try {
            setWeights(fetchBodyWeights.inYear(currentYear))
            oldestLoadedYear = currentYear
            _state.value = LoadingState.loaded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
            _state.value = LoadingState.Error(error)
        }
        loadEarlierYearsIfNeeded()
    }

    suspend fun onRetryTapped() {
        _state.value = LoadingState.Loading
        onAppear()
    }

    suspend fun loadEarlierYearsIfNeeded() {
        if (_isLoadingEarlierYear.value) return
        _isLoadingEarlierYear.value = true
        try {
            while (true) {
                val oldest = oldestLoadedYear ?: return
                if (oldest <= maxOf(BodyWeightDayKey.year(_period.value.start, zone), Constants.BodyWeight.FIRST_YEAR)) return
                try {
                    val earlier = fetchBodyWeights.inYear(oldest - 1)
                    setWeights((_weights.value + earlier).sortedByDescending { it.dayKey })
                    oldestLoadedYear = oldest - 1
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.error(error, Constants.LogCategory.DASHBOARD)
                    alertItem.value = AlertItem(titleRes = R.string.weightHistory_error_loadFailed)
                    return
                }
            }
        } finally {
            _isLoadingEarlierYear.value = false
        }
    }

    fun onDisappear() {
        if (!hasChanges) return
        hasChanges = false
        onChanged()
    }

    fun onRangeChanged(range: BodyWeightRange) {
        _range.value = range
        _periodOffset.value = offsetOfSelectedDay(range)
        refreshChart()
    }

    suspend fun onPreviousPeriod() {
        if (!canPageBack) return
        _periodOffset.value -= 1
        refreshChart()
        loadEarlierYearsIfNeeded()
    }

    fun onNextPeriod() {
        if (!canPageForward) return
        _periodOffset.value += 1
        refreshChart()
    }

    fun onDeleteRequested(weight: BodyWeightDomain) {
        pendingDeletion = weight
        isDeleteConfirmationVisible.value = true
    }

    suspend fun onDeleteConfirmed() {
        val weight = pendingDeletion ?: return
        pendingDeletion = null
        try {
            deleteBodyWeight(weight.dayKey)
            hasChanges = true
            setWeights(_weights.value.filterNot { it.dayKey == weight.dayKey })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
            alertItem.value = AlertItem(titleRes = R.string.weightHistory_error_deleteFailed)
        }
    }

    // MARK: - Private

    private fun setWeights(weights: List<BodyWeightDomain>) {
        _weights.value = weights
        refreshChart()
    }

    private fun offsetOfSelectedDay(range: BodyWeightRange): Int {
        val today = now().atZone(zone).toLocalDate()
        val selected = selectedDay.atZone(zone).toLocalDate()
        val periods = when (range) {
            BodyWeightRange.WEEK -> {
                val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
                ChronoUnit.WEEKS.between(
                    selected.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)),
                    today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)),
                )
            }
            BodyWeightRange.MONTH -> ChronoUnit.MONTHS.between(selected.withDayOfMonth(1), today.withDayOfMonth(1))
            BodyWeightRange.YEAR -> (today.year - selected.year).toLong()
        }
        return -maxOf(periods, 0L).toInt()
    }

    private fun refreshChart() {
        val anchor = now().atZone(zone).toLocalDate()
        val (first, nextFirst) = when (_range.value) {
            BodyWeightRange.WEEK -> {
                val start = anchor.plusWeeks(_periodOffset.value.toLong()).with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
                start to start.plusWeeks(1)
            }
            BodyWeightRange.MONTH -> {
                val start = anchor.plusMonths(_periodOffset.value.toLong()).withDayOfMonth(1)
                start to start.plusMonths(1)
            }
            BodyWeightRange.YEAR -> {
                val start = LocalDate.of(anchor.year + _periodOffset.value, 1, 1)
                start to start.plusYears(1)
            }
        }
        val interval = BodyWeightPeriod(first.atStartOfDay(zone).toInstant(), nextFirst.atStartOfDay(zone).toInstant())
        _period.value = interval
        _chartPoints.value = _weights.value
            .mapNotNull { weight ->
                val date = BodyWeightDayKey.date(weight.dayKey, zone)
                if (date != null && interval.contains(date)) BodyWeightChartPoint(date, weight.kilograms) else null
            }
            .sortedBy { it.date }
    }
}
