package antoni.kalorie.features.settings

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.usecases.CreateMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCaseProtocol
import antoni.kalorie.core.usecases.SaveAppSettingsUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMealTypeTimesUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.core.utils.withAddedMinutes
import antoni.kalorie.core.utils.zoned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

class SettingsViewModel(
    mealTypes: List<MealTypeDomain>,
    appSettings: AppSettingsDomain,
    private val onMealTypesChanged: () -> Unit = {},
    private val onAppSettingsChanged: (AppSettingsDomain) -> Unit = {},
    private val saveAppSettings: SaveAppSettingsUseCaseProtocol,
    private val createMealType: CreateMealTypeUseCaseProtocol,
    private val deleteMealType: DeleteMealTypeUseCaseProtocol,
    private val updateMealTypeTimes: UpdateMealTypeTimesUseCaseProtocol,
    private val fetchMaintainerClaim: FetchMaintainerClaimUseCaseProtocol,
    val hasLoadedAppSettings: Boolean = true,
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
    val backStack = mutableStateListOf<SettingsDestination>(SettingsDestination.Settings)
    private val _isMaintainer = MutableStateFlow(false)
    val isMaintainer: StateFlow<Boolean> = _isMaintainer
    val alertItem = MutableStateFlow<AlertItem?>(null)
    private val _appSettings = MutableStateFlow(appSettings)
    val appSettings: StateFlow<AppSettingsDomain> = _appSettings

    private var persistedAppSettings = appSettings
    private val saveMutex = Mutex()

    // MARK: - Functions

    suspend fun onAppear() {
        try {
            _isMaintainer.value = fetchMaintainerClaim()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.SETTINGS)
        }
    }

    suspend fun onShowBodyWeightChanged(isShown: Boolean) {
        apply(_appSettings.value.copy(showBodyWeight = isShown))
    }

    suspend fun onBodyWeightUnitChanged(unit: BodyWeightUnit) {
        apply(_appSettings.value.copy(bodyWeightUnit = unit))
    }

    private suspend fun apply(updated: AppSettingsDomain) {
        if (!hasLoadedAppSettings || updated == _appSettings.value) return
        _appSettings.value = updated
        withContext(NonCancellable) { saveMutex.withLock { persist(updated) } }
    }

    private suspend fun persist(updated: AppSettingsDomain) {
        try {
            saveAppSettings(updated)
            persistedAppSettings = updated
            onAppSettingsChanged(updated)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.SETTINGS)
            if (_appSettings.value != updated) return
            _appSettings.value = persistedAppSettings
            alertItem.value = AlertItem(titleRes = R.string.settings_error_saveFailed)
        }
    }

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
                is CreateMealTypeError.EmptyName -> AlertItem(titleRes = R.string.settings_error_emptyName)
                is CreateMealTypeError.DuplicateName -> AlertItem(titleRes = R.string.settings_error_duplicateName)
                is CreateMealTypeError.TimeConflict -> AlertItem(titleRes = R.string.settings_error_timeConflict)
                is CreateMealTypeError.DurationTooShort -> AlertItem(titleRes = R.string.settings_error_durationTooShort)
                else -> {
                    Log.error(error, Constants.LogCategory.SETTINGS)
                    AlertItem(titleRes = R.string.settings_error_unexpected)
                }
            }
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onDelete(index: Int) {
        if (mealTypes.value.size <= 1) {
            alertItem.value = AlertItem(titleRes = R.string.settings_error_lastMealType)
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
            Log.error(error, Constants.LogCategory.SETTINGS)
            alertItem.value = AlertItem(titleRes = R.string.settings_error_deleteError)
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
            mealType.copy(startMinutes = startMinutes, endMinutes = endMinutes)
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
            Log.error(error, Constants.LogCategory.SETTINGS)
            alertItem.value = AlertItem(titleRes = R.string.settings_error_unexpected)
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
