package antoni.kalorie.features.export

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodExportFormat
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.GenerateFoodExportUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ExportedFile(val file: File, val format: FoodExportFormat)

class ExportViewModel(
    private val mealTypes: List<MealTypeDomain>,
    private val generateFoodExport: GenerateFoodExportUseCaseProtocol,
    now: Instant = Instant.now(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    // MARK: - State

    enum class State {
        IDLE,
        GENERATING,
    }

    // MARK: - Properties

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state
    val fromDate = MutableStateFlow(now.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant())
    val toDate = MutableStateFlow(now)
    val format = MutableStateFlow(FoodExportFormat.PDF)
    val exportedFile = MutableStateFlow<ExportedFile?>(null)
    val alertItem = MutableStateFlow<AlertItem?>(null)

    val isExportDisabled: Boolean
        get() = _state.value == State.GENERATING ||
            fromDate.value.atZone(zone).toLocalDate().isAfter(toDate.value.atZone(zone).toLocalDate())

    // MARK: - Functions

    suspend fun onExportTapped() {
        if (isExportDisabled) return
        _state.value = State.GENERATING
        try {
            val file = generateFoodExport(fromDate.value, toDate.value, format.value, mealTypes)
            exportedFile.value = ExportedFile(file, format.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.EXPORT)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = State.IDLE
        }
    }

    fun onShareFinished() {
        exportedFile.value = null
    }
}
