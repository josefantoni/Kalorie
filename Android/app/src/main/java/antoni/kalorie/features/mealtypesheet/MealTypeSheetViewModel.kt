package antoni.kalorie.features.mealtypesheet

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.CreateMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.core.utils.withAddedMinutes
import antoni.kalorie.core.utils.zoned
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MealTypeSheetViewModel(
    mealTypes: List<MealTypeDomain>,
    private val onMealTypesChanged: () -> Unit = {},
    private val createMealType: CreateMealTypeUseCaseProtocol,
    private val deleteMealType: DeleteMealTypeUseCaseProtocol,
    private val updateMealTypeTimes: UpdateMealTypeTimesUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.loaded)
    val state: StateFlow<LoadingState<Unit>> = _state
    val mealTypes = MutableStateFlow(mealTypes)
    private val _hasPendingReorder = MutableStateFlow(false)
    val hasPendingReorder: StateFlow<Boolean> = _hasPendingReorder
    val newMealName = MutableStateFlow("")
    val newMealStart = MutableStateFlow(Instant.now())
    val newMealEnd = MutableStateFlow(Instant.now())
    val isAddFormVisible = MutableStateFlow(false)
    val isExportPushed = MutableStateFlow(false)
    val alertItem = MutableStateFlow<AlertItem?>(null)

    // MARK: - Functions

    suspend fun onCreateMealType() {
        _state.value = LoadingState.Loading
        try {
            val newMeal = createMealType(
                name = newMealName.value,
                startMinutes = newMealStart.value.minutesSinceMidnight(),
                endMinutes = newMealEnd.value.minutesSinceMidnight(),
                existingMealTypes = mealTypes.value,
            )
            mealTypes.value = (mealTypes.value + newMeal).sortedBy { it.startMinutes }
            isAddFormVisible.value = false
            newMealName.value = ""
            onMealTypesChanged()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            alertItem.value = when (error) {
                is CreateMealTypeError.EmptyName -> AlertItem(titleRes = R.string.mealTypeSheet_error_emptyName)
                is CreateMealTypeError.DuplicateName -> AlertItem(titleRes = R.string.mealTypeSheet_error_duplicateName)
                is CreateMealTypeError.TimeConflict -> AlertItem(titleRes = R.string.mealTypeSheet_error_timeConflict)
                is CreateMealTypeError.DurationTooShort -> AlertItem(titleRes = R.string.mealTypeSheet_error_durationTooShort)
                else -> {
                    Log.error(error, Constants.LogCategory.MEAL_TYPE_SHEET)
                    AlertItem(titleRes = R.string.mealTypeSheet_error_unexpected)
                }
            }
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onDelete(index: Int) {
        if (mealTypes.value.size <= 1) {
            alertItem.value = AlertItem(titleRes = R.string.mealTypeSheet_error_lastMealType)
            return
        }
        _state.value = LoadingState.Loading
        val mealType = mealTypes.value[index]
        try {
            deleteMealType(mealType)
            mealTypes.value = mealTypes.value.filter { it.id != mealType.id }
            onMealTypesChanged()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MEAL_TYPE_SHEET)
            alertItem.value = AlertItem(titleRes = R.string.mealTypeSheet_error_deleteError)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    fun onMove(from: Int, to: Int) {
        val originalTimes = mealTypes.value.map { it.startMinutes to it.endMinutes }
        val moved = mealTypes.value.toMutableList()
        val movedMealType = moved.removeAt(from)
        moved.add(if (to > from) to - 1 else to, movedMealType)
        mealTypes.value = moved.mapIndexed { index, mealType ->
            val (startMinutes, endMinutes) = originalTimes[index]
            MealTypeDomain(id = mealType.id, name = mealType.name, startMinutes = startMinutes, endMinutes = endMinutes)
        }
        _hasPendingReorder.value = true
    }

    suspend fun onSaveReorder() {
        if (!_hasPendingReorder.value) return
        _state.value = LoadingState.Loading
        _hasPendingReorder.value = false
        try {
            updateMealTypeTimes(mealTypes.value)
            onMealTypesChanged()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MEAL_TYPE_SHEET)
            alertItem.value = AlertItem(titleRes = R.string.mealTypeSheet_error_unexpected)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    fun onShowAddForm() {
        val latestEndMinutes = mealTypes.value.maxOfOrNull { it.endMinutes }
        if (latestEndMinutes == null) {
            newMealStart.value = Instant.now()
            newMealEnd.value = Instant.now().withAddedMinutes(30.0)
            isAddFormVisible.value = true
            return
        }
        val now = Instant.now().zoned()
        val possibleStart = now.toLocalDate().atStartOfDay().plusMinutes(latestEndMinutes.toLong()).atZone(now.zone).toInstant()
        newMealStart.value = possibleStart
        newMealEnd.value = possibleStart.withAddedMinutes(30.0)
        isAddFormVisible.value = true
    }
}
