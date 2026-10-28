package antoni.kalorie.features.dashboard

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import antoni.kalorie.BuildConfig
import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightWindow
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.mealType
import antoni.kalorie.core.models.resolvedMealTypeId
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseProtocol
import antoni.kalorie.core.usecases.CopyFoodsConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.FetchAppSettingsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedInRangeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseProtocol
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.formatCacheKey
import antoni.kalorie.core.utils.isFirestoreUnreachable
import antoni.kalorie.core.utils.isSameDay
import antoni.kalorie.core.utils.zoned
import antoni.kalorie.macrokit.Macros
import antoni.kalorie.macrokit.total
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class DailyMacros(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatSaturated: Double,
    val fiber: Double,
    val salt: Double,
) {
    constructor(foods: List<FoodConsumedDomain>) : this(
        foods
            .map {
                Macros(
                    calories = it.calories,
                    protein = it.protein,
                    carbohydrate = it.carbohydrate,
                    carbohydrateSugar = it.carbohydrateSugar,
                    fat = it.fat,
                    fatSaturated = it.fatSaturated ?: 0.0,
                    fatUnsaturated = it.fatUnsaturated,
                    fiber = it.fiber ?: 0.0,
                    salt = it.salt,
                )
            }
            .total(),
    )

    private constructor(total: Macros) : this(
        calories = total.calories,
        protein = total.protein,
        carbs = total.carbohydrate,
        carbohydrateSugar = total.carbohydrateSugar,
        fat = total.fat,
        fatSaturated = total.fatSaturated,
        fiber = total.fiber,
        salt = total.salt,
    )
}

data class FoodGroup(
    val mealType: MealTypeDomain?,
    val foods: List<FoodConsumedDomain>,
)

private const val COPY_CHECKMARK_MILLIS = 1000L
private const val WEIGHT_CHECKMARK_MILLIS = 1000L
private val SIGN_IN_SPOTLIGHT_INTERVAL = Duration.ofDays(7)

class DashboardViewModel(
    private val fetchMealTypes: FetchMealTypesUseCaseProtocol,
    private val fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseProtocol,
    private val fetchFoodsConsumedInRange: FetchFoodsConsumedInRangeUseCaseProtocol,
    private val setupDefaultMeals: SetupDefaultMealsUseCaseProtocol,
    private val confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseProtocol,
    private val deleteFoodConsumed: DeleteFoodConsumedUseCaseProtocol,
    private val copyFoodsConsumed: CopyFoodsConsumedUseCaseProtocol,
    private val assignFoodMealType: AssignFoodMealTypeUseCaseProtocol,
    private val fetchBodyWeights: FetchBodyWeightsUseCaseProtocol,
    private val fetchAppSettings: FetchAppSettingsUseCaseProtocol,
    private val saveBodyWeight: SaveBodyWeightUseCaseProtocol,
    private val authProvider: AuthProviderProtocol,
    private val signInSpotlightStore: SignInSpotlightStoreProtocol,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Loading)
    val state: StateFlow<LoadingState<Unit>> = _state
    val mealTypes = MutableStateFlow<List<MealTypeDomain>>(emptyList())
    val foodsConsumed = MutableStateFlow<List<FoodConsumedDomain>>(emptyList())
    val selectedDay = MutableStateFlow(Instant.now())
    val showSettings = MutableStateFlow(false)
    val showAddFoodSheet = MutableStateFlow(false)
    val showCalendarSheet = MutableStateFlow(false)
    val showAccountSheet = MutableStateFlow(false)
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val backStack = mutableStateListOf<DashboardDestination>(DashboardDestination.Dashboard)
    val isDeleteConfirmationVisible = MutableStateFlow(false)
    val copyPopoverIndex = MutableStateFlow<Int?>(null)
    val macroPopoverIndex = MutableStateFlow<Int?>(null)
    private val _isSignInSpotlightVisible = MutableStateFlow(false)
    val isSignInSpotlightVisible: StateFlow<Boolean> = _isSignInSpotlightVisible
    val copyTargetDay = MutableStateFlow(Instant.now())
    val copyTargetMealTypeId = MutableStateFlow<String?>(null)
    private val _isCopying = MutableStateFlow(false)
    val isCopying: StateFlow<Boolean> = _isCopying
    private val _showCopyCheckmark = MutableStateFlow(false)
    val showCopyCheckmark: StateFlow<Boolean> = _showCopyCheckmark
    private val _activeDaysInMonth = MutableStateFlow<Set<Int>>(emptySet())
    val activeDaysInMonth: StateFlow<Set<Int>> = _activeDaysInMonth
    private val _moveFeedbackTrigger = MutableStateFlow(0)
    val moveFeedbackTrigger: StateFlow<Int> = _moveFeedbackTrigger
    private val _recentWeights = MutableStateFlow<List<BodyWeightDomain>>(emptyList())
    val recentWeights: StateFlow<List<BodyWeightDomain>> = _recentWeights
    private val _laterWeights = MutableStateFlow<List<BodyWeightDomain>>(emptyList())
    val laterWeights: StateFlow<List<BodyWeightDomain>> = _laterWeights
    private val _shownWeightsKey = MutableStateFlow<String?>(null)
    val shownWeightsKey: StateFlow<String?> = _shownWeightsKey
    private val _appSettings = MutableStateFlow(AppSettingsDomain.defaults)
    val appSettings: StateFlow<AppSettingsDomain> = _appSettings
    private val _hasLoadedSettings = MutableStateFlow(false)
    val hasLoadedSettings: StateFlow<Boolean> = _hasLoadedSettings
    val isWeightPopoverVisible = MutableStateFlow(false)
    val weightPickerKilograms = MutableStateFlow(Constants.BodyWeight.DEFAULT_KILOGRAMS)
    private val _isSavingWeight = MutableStateFlow(false)
    val isSavingWeight: StateFlow<Boolean> = _isSavingWeight
    private val _showWeightCheckmark = MutableStateFlow(false)
    val showWeightCheckmark: StateFlow<Boolean> = _showWeightCheckmark

    private var activeDaysMonth = Instant.now()
    private var isViewingToday = true
    private var hasCompletedInitialLoad = false
    private var monthCache = mutableMapOf<String, List<FoodConsumedDomain>>()
    private val cachedMonthKeys = mutableSetOf<String>()
    private var foodPendingDeletion: FoodConsumedDomain? = null
    private var weightCache = mutableMapOf<String, BodyWeightWindow>()
    private var weightCacheGeneration = 0
    private var weightPickerDay: Instant? = null
    private var settingsChangeCount = 0

    val isWeightCircleVisible: Boolean
        get() = _hasLoadedSettings.value && _appSettings.value.showBodyWeight

    val canLogWeight: Boolean
        get() {
            val selectedKey = BodyWeightDayKey.make(selectedDay.value)
            val todayKey = BodyWeightDayKey.make(now())
            if (_shownWeightsKey.value != selectedKey) return false
            return selectedKey <= todayKey
        }

    val hasWeightForSelectedDay: Boolean
        get() = _recentWeights.value.firstOrNull()?.dayKey == BodyWeightDayKey.make(selectedDay.value)

    val dailyMacros: DailyMacros
        get() = DailyMacros(foodsConsumed.value)

    val groupedFoods: List<FoodGroup>
        get() {
            val foodsByMealTypeId = mutableMapOf<String, MutableList<FoodConsumedDomain>>()
            val unassigned = mutableListOf<FoodConsumedDomain>()

            for (food in foodsConsumed.value) {
                val resolvedMealTypeId = mealTypes.value.resolvedMealTypeId(food)
                if (resolvedMealTypeId != null) {
                    foodsByMealTypeId.getOrPut(resolvedMealTypeId) { mutableListOf() } += food
                } else {
                    unassigned += food
                }
            }

            val result = mealTypes.value
                .sortedBy { it.startMinutes }
                .mapNotNull { mealType -> foodsByMealTypeId[mealType.id]?.let { FoodGroup(mealType, it) } }
                .toMutableList()
            if (unassigned.isNotEmpty()) {
                result += FoodGroup(mealType = null, foods = unassigned)
            }
            return result
        }

    // MARK: - Functions

    suspend fun onAppear() {
        if (hasCompletedInitialLoad) return
        selectedDay.value = Instant.now()
        isViewingToday = true
        _state.value = LoadingState.Loading
        coroutineScope {
            val settings = async { refreshSettings() }
            val weights = async { loadWeights(selectedDay.value) }
            perform {
                refreshMealTypes()
                loadMonth(selectedDay.value)
                foodsConsumed.value = foodsFromCache(selectedDay.value)
                showActiveDays(selectedDay.value)
                settings.await()
                weights.await()
                showSignInSpotlightIfNeeded()
            }
        }
        _state.value = LoadingState.loaded
        hasCompletedInitialLoad = true
    }

    suspend fun onRefresh() {
        if (!hasCompletedInitialLoad) return
        advanceSelectedDayIfNeeded()
        invalidateWeightCache()
        coroutineScope {
            val settings = async { refreshSettings() }
            val weights = async { loadWeights(selectedDay.value) }
            perform {
                refreshMealTypes()
                invalidateCache(selectedDay.value)
                loadMonth(selectedDay.value)
                foodsConsumed.value = foodsFromCache(selectedDay.value)
                showActiveDays(selectedDay.value)
                settings.await()
                weights.await()
                showSignInSpotlightIfNeeded()
            }
        }
    }

    suspend fun onForeground() {
        if (!hasCompletedInitialLoad) return
        advanceSelectedDayIfNeeded()
        coroutineScope {
            val settings = async { refreshSettings() }
            val weights = async { loadWeights(selectedDay.value, forceReload = true) }
            perform {
                refreshMealTypes()
                reloadDay(selectedDay.value)
                foodsConsumed.value = foodsFromCache(selectedDay.value)
                settings.await()
                weights.await()
                showSignInSpotlightIfNeeded()
            }
        }
    }

    fun onSignInSpotlightSignInTapped() {
        _isSignInSpotlightVisible.value = false
        showAccountSheet.value = true
    }

    fun onSignInSpotlightDismissed() {
        _isSignInSpotlightVisible.value = false
    }

    fun onSignInSpotlightDebugTriggered() {
        if (BuildConfig.DEBUG) _isSignInSpotlightVisible.value = true
    }

    suspend fun onFoodConsumedUpdated() {
        perform {
            reloadDay(selectedDay.value)
            foodsConsumed.value = foodsFromCache(selectedDay.value)
            showSignInSpotlightIfNeeded()
        }
    }

    fun onAddFoodSheetDismissed() {
        showAddFoodSheet.value = false
        showSignInSpotlightIfNeeded()
    }

    fun onDeleteRequested(food: FoodConsumedDomain) {
        foodPendingDeletion = food
        isDeleteConfirmationVisible.value = true
    }

    suspend fun onDeleteConfirmed() {
        val food = foodPendingDeletion ?: return
        foodPendingDeletion = null
        perform(onFailure = { alertItem.value = AlertItem(titleRes = R.string.dashboard_error_deleteFailed) }) {
            deleteFoodConsumed(id = food.id)
            reloadDay(selectedDay.value)
            foodsConsumed.value = foodsFromCache(selectedDay.value)
        }
    }

    fun onFoodDropped(food: FoodConsumedDomain, mealType: MealTypeDomain) {
        viewModelScope.launch { onFoodMoved(food, mealType) }
    }

    suspend fun onFoodMoved(food: FoodConsumedDomain, mealType: MealTypeDomain) {
        if (mealTypes.value.resolvedMealTypeId(food) == mealType.id) return
        replaceInCache(food.copy(mealTypeId = mealType.id))
        perform(
            onFailure = {
                if (cachedFood(food)?.mealTypeId == mealType.id) replaceInCache(food)
                alertItem.value = AlertItem(titleRes = R.string.dashboard_error_moveFailed)
            },
        ) {
            assignFoodMealType(food, mealTypeId = mealType.id)
            _moveFeedbackTrigger.value += 1
        }
    }

    fun onCopyRequested(mealType: MealTypeDomain?, index: Int) {
        val now = Instant.now()
        copyTargetDay.value = now
        copyTargetMealTypeId.value = mealTypes.value.mealType(now)?.id ?: mealType?.id ?: mealTypes.value.firstOrNull()?.id
        copyPopoverIndex.value = index
    }

    fun canCopy(mealType: MealTypeDomain?): Boolean {
        val targetMealTypeId = copyTargetMealTypeId.value
        if (_isCopying.value || _showCopyCheckmark.value || targetMealTypeId == null) return false
        val isSameDay = copyTargetDay.value.isSameDay(selectedDay.value)
        return !(isSameDay && targetMealTypeId == mealType?.id)
    }

    suspend fun onCopyConfirmed(foods: List<FoodConsumedDomain>, mealType: MealTypeDomain?) {
        val targetMealTypeId = copyTargetMealTypeId.value
        if (!canCopy(mealType) || targetMealTypeId == null) return
        _isCopying.value = true
        var isCopied = false
        perform(onFailure = { alertItem.value = AlertItem(titleRes = R.string.dashboard_error_copyFailed) }) {
            copyFoodsConsumed(foods, copyTargetDay.value, targetMealTypeId, mealTypes.value)
            isCopied = true
        }
        _isCopying.value = false
        if (!isCopied) return
        _showCopyCheckmark.value = true
        // The caller's scope can be cancelled mid-delay (rotation); the flags must still reset or Copy stays disabled.
        try {
            reloadAfterCopy()
            delay(COPY_CHECKMARK_MILLIS)
        } finally {
            copyPopoverIndex.value = null
            _showCopyCheckmark.value = false
        }
    }

    suspend fun onMealTypesChanged() {
        perform { refreshMealTypes() }
    }

    suspend fun onDayChanged(date: Instant) {
        isViewingToday = date.isSameDay(Instant.now())
        coroutineScope {
            val weights = async { loadWeights(date) }
            loadFoods(date)
            weights.await()
        }
    }

    suspend fun onDaySelected(date: Instant) {
        isViewingToday = date.isSameDay(Instant.now())
        selectedDay.value = date
        showCalendarSheet.value = false
        coroutineScope {
            val weights = async { loadWeights(date) }
            loadFoods(date)
            weights.await()
        }
    }

    fun onWeightBadgeTapped() {
        if (!canLogWeight) return
        weightPickerKilograms.value = _recentWeights.value.firstOrNull()?.kilograms ?: Constants.BodyWeight.DEFAULT_KILOGRAMS
        weightPickerDay = selectedDay.value
        isWeightPopoverVisible.value = true
    }

    suspend fun onWeightConfirmed() {
        if (_isSavingWeight.value || _showWeightCheckmark.value) return
        _isSavingWeight.value = true
        try {
            saveBodyWeight(weightPickerKilograms.value, weightPickerDay ?: selectedDay.value)
        } catch (error: CancellationException) {
            _isSavingWeight.value = false
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
            alertItem.value = AlertItem(titleRes = R.string.dashboard_error_weightSaveFailed)
            _isSavingWeight.value = false
            return
        }
        _isSavingWeight.value = false
        _showWeightCheckmark.value = true
        try {
            onWeightsChanged()
            delay(WEIGHT_CHECKMARK_MILLIS)
        } finally {
            isWeightPopoverVisible.value = false
            _showWeightCheckmark.value = false
        }
    }

    suspend fun onWeightsChanged() {
        invalidateWeightCache()
        val key = BodyWeightDayKey.make(selectedDay.value)
        val isLoaded = reloadWeights(selectedDay.value)
        if (!isLoaded && _shownWeightsKey.value == key) clearShownWeights()
    }

    fun onAppSettingsChanged(settings: AppSettingsDomain) {
        settingsChangeCount += 1
        _appSettings.value = settings
        _hasLoadedSettings.value = true
    }

    suspend fun onCalendarMonthChanged(month: Instant) {
        if (monthCacheKey(month) in cachedMonthKeys) {
            showActiveDays(month)
        } else {
            perform {
                loadMonth(month)
                showActiveDays(month)
            }
        }
    }

    fun onCalendarDismissed() {
        showCalendarSheet.value = false
        showActiveDays(selectedDay.value)
    }

    // MARK: - Private

    private val isAnyPresentationActive: Boolean
        get() = showSettings.value ||
            showAddFoodSheet.value ||
            showCalendarSheet.value ||
            showAccountSheet.value ||
            alertItem.value != null ||
            isDeleteConfirmationVisible.value ||
            copyPopoverIndex.value != null ||
            macroPopoverIndex.value != null ||
            isWeightPopoverVisible.value

    private fun showSignInSpotlightIfNeeded() {
        if (!authProvider.isAnonymous || monthCache.values.none { it.isNotEmpty() } || _isSignInSpotlightVisible.value || isAnyPresentationActive) return
        val currentInstant = now()
        val lastShownAt = signInSpotlightStore.lastShownAt
        if (lastShownAt != null && Duration.between(lastShownAt, currentInstant) < SIGN_IN_SPOTLIGHT_INTERVAL) return
        signInSpotlightStore.lastShownAt = currentInstant
        _isSignInSpotlightVisible.value = true
    }

    private suspend fun perform(
        onFailure: (Exception) -> Unit = { alertItem.value = unknownErrorAlertItem(it) },
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
            onFailure(error)
        }
    }

    private fun unknownErrorAlertItem(error: Throwable): AlertItem = if (error.isFirestoreUnreachable) {
        AlertItem(titleRes = R.string.common_error_offline, messageRes = R.string.common_error_offline_message)
    } else {
        AlertItem(titleRes = R.string.common_error_unknown, messageRes = R.string.common_error_unknown_message)
    }

    private suspend fun reloadAfterCopy() {
        if (monthCacheKey(copyTargetDay.value) !in cachedMonthKeys) return
        perform {
            reloadDay(copyTargetDay.value)
            if (copyTargetDay.value.isSameDay(selectedDay.value)) {
                foodsConsumed.value = foodsFromCache(selectedDay.value)
            }
        }
    }

    private suspend fun refreshSettings() {
        val changeCountBeforeRead = settingsChangeCount
        try {
            val settings = fetchAppSettings()
            if (changeCountBeforeRead != settingsChangeCount) return
            _appSettings.value = settings
            _hasLoadedSettings.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
        }
    }

    private suspend fun loadWeights(date: Instant, forceReload: Boolean = false) {
        reloadWeights(date, forceReload)
    }

    private suspend fun reloadWeights(date: Instant, forceReload: Boolean = false): Boolean {
        val key = BodyWeightDayKey.make(date)
        val cached = weightCache[key]
        if (!forceReload && cached != null) {
            if (BodyWeightDayKey.make(selectedDay.value) == key) show(cached, key)
            return true
        }
        if (_shownWeightsKey.value != key && BodyWeightDayKey.make(selectedDay.value) == key) clearShownWeights()
        val generation = weightCacheGeneration
        return try {
            val window = coroutineScope {
                val earlier = async { fetchBodyWeights.upTo(date) }
                val later = async { loadLaterWeights(date) }
                BodyWeightWindow(earlier = earlier.await(), later = later.await())
            }
            if (generation != weightCacheGeneration) return true
            weightCache[key] = window
            if (BodyWeightDayKey.make(selectedDay.value) == key) show(window, key)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
            false
        }
    }

    private fun invalidateWeightCache() {
        weightCache = mutableMapOf()
        weightCacheGeneration += 1
    }

    private suspend fun loadLaterWeights(date: Instant): List<BodyWeightDomain> {
        if (BodyWeightDayKey.make(date) >= BodyWeightDayKey.make(now())) return emptyList()
        return fetchBodyWeights.after(date)
    }

    private fun clearShownWeights() {
        _shownWeightsKey.value = null
        _recentWeights.value = emptyList()
        _laterWeights.value = emptyList()
    }

    private fun show(window: BodyWeightWindow, key: String) {
        _shownWeightsKey.value = key
        _recentWeights.value = window.earlier
        _laterWeights.value = window.later
    }

    private suspend fun refreshMealTypes() {
        var types = fetchMealTypes()
        if (types.isEmpty() && confirmMealTypesEmpty()) {
            types = setupDefaultMeals()
        }
        mealTypes.value = types
    }

    private fun advanceSelectedDayIfNeeded() {
        if (!isViewingToday) return
        if (selectedDay.value.isSameDay(Instant.now())) return
        selectedDay.value = Instant.now()
    }

    private suspend fun loadFoods(date: Instant) {
        if (monthCacheKey(date) in cachedMonthKeys) {
            foodsConsumed.value = foodsFromCache(date)
            showActiveDays(date)
        } else {
            perform {
                loadMonth(date)
                foodsConsumed.value = foodsFromCache(date)
                showActiveDays(date)
            }
        }
    }

    private suspend fun loadMonth(date: Instant) {
        val foods = fetchFoodsConsumedForMonth(date)
        populateCache(foods, date)
    }

    private suspend fun reloadDay(date: Instant) {
        if (monthCacheKey(date) in cachedMonthKeys) {
            val day = date.zoned().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val foods = fetchFoodsConsumedInRange(day, day)
            monthCache[dayCacheKey(day)] = foods
            _activeDaysInMonth.value = computeActiveDays(activeDaysMonth)
        } else {
            loadMonth(date)
            showActiveDays(date)
        }
    }

    private fun showActiveDays(month: Instant) {
        activeDaysMonth = month
        _activeDaysInMonth.value = computeActiveDays(month)
    }

    private fun monthCacheKey(date: Instant): String = date.formatCacheKey(Constants.DateFormat.MONTH_CACHE_KEY)

    private fun dayCacheKey(date: Instant): String = date.formatCacheKey(Constants.DateFormat.DAY_CACHE_KEY)

    private fun populateCache(foods: List<FoodConsumedDomain>, month: Instant) {
        val key = monthCacheKey(month)
        monthCache = monthCache.filterKeys { !it.startsWith(key) }.toMutableMap()
        cachedMonthKeys += key
        for ((dayKey, dayFoods) in foods.groupBy { dayCacheKey(it.date) }) {
            monthCache[dayKey] = monthCache[dayKey].orEmpty() + dayFoods
        }
    }

    private fun replaceInCache(food: FoodConsumedDomain) {
        val key = dayCacheKey(food.date)
        val foods = monthCache[key] ?: return
        val index = foods.indexOfFirst { it.id == food.id }
        if (index < 0) return
        monthCache[key] = foods.toMutableList().also { it[index] = food }
        if (dayCacheKey(selectedDay.value) == key) {
            foodsConsumed.value = foodsFromCache(selectedDay.value)
        }
    }

    private fun cachedFood(food: FoodConsumedDomain): FoodConsumedDomain? = monthCache[dayCacheKey(food.date)]?.firstOrNull { it.id == food.id }

    private fun foodsFromCache(date: Instant): List<FoodConsumedDomain> = monthCache[dayCacheKey(date)].orEmpty()

    private fun computeActiveDays(month: Instant): Set<Int> {
        val key = monthCacheKey(month)
        val result = mutableSetOf<Int>()
        for ((dayKey, foods) in monthCache) {
            if (!dayKey.startsWith(key) || foods.isEmpty()) continue
            val parts = dayKey.split("-")
            val day = parts.getOrNull(2)?.toIntOrNull()
            if (parts.size == 3 && day != null) {
                result += day
            }
        }
        return result
    }

    private fun invalidateCache(date: Instant) {
        val key = monthCacheKey(date)
        monthCache = monthCache.filterKeys { !it.startsWith(key) }.toMutableMap()
        cachedMonthKeys -= key
    }
}
