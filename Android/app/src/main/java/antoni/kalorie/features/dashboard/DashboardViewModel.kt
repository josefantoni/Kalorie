package antoni.kalorie.features.dashboard

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.resolvedMealTypeId
import antoni.kalorie.core.usecases.ConfirmMealTypesEmptyUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteFoodConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodsConsumedForMonthUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.SetupDefaultMealsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.formatCacheKey
import antoni.kalorie.core.utils.isFirestoreUnreachable
import antoni.kalorie.core.utils.isSameDay
import antoni.kalorie.macrokit.Macros
import antoni.kalorie.macrokit.total
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DailyMacros(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatUnsaturated: Double,
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
                    fatUnsaturated = it.fatUnsaturated,
                    fiber = it.fiber ?: 0.0,
                    salt = it.salt,
                )
            }
            .total()
    )

    private constructor(total: Macros) : this(
        calories = total.calories,
        protein = total.protein,
        carbs = total.carbohydrate,
        carbohydrateSugar = total.carbohydrateSugar,
        fat = total.fat,
        fatUnsaturated = total.fatUnsaturated,
        fiber = total.fiber,
        salt = total.salt,
    )
}

data class FoodGroup(
    val mealType: MealTypeDomain?,
    val foods: List<FoodConsumedDomain>,
)

class DashboardViewModel(
    private val fetchMealTypes: FetchMealTypesUseCaseProtocol,
    private val fetchFoodsConsumedForMonth: FetchFoodsConsumedForMonthUseCaseProtocol,
    private val setupDefaultMeals: SetupDefaultMealsUseCaseProtocol,
    private val confirmMealTypesEmpty: ConfirmMealTypesEmptyUseCaseProtocol,
    private val deleteFoodConsumed: DeleteFoodConsumedUseCaseProtocol,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Loading)
    val state: StateFlow<LoadingState<Unit>> = _state
    val mealTypes = MutableStateFlow<List<MealTypeDomain>>(emptyList())
    val foodsConsumed = MutableStateFlow<List<FoodConsumedDomain>>(emptyList())
    val selectedDay = MutableStateFlow(Instant.now())
    val showMealTypeSheet = MutableStateFlow(false)
    val showAddFoodSheet = MutableStateFlow(false)
    val showCalendarSheet = MutableStateFlow(false)
    val showAccountSheet = MutableStateFlow(false)
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val backStack = mutableStateListOf<DashboardDestination>(DashboardDestination.Dashboard)
    val isDeleteConfirmationVisible = MutableStateFlow(false)
    private val _activeDaysInMonth = MutableStateFlow<Set<Int>>(emptySet())
    val activeDaysInMonth: StateFlow<Set<Int>> = _activeDaysInMonth

    private var isViewingToday = true
    private var hasCompletedInitialLoad = false
    private var monthCache = mutableMapOf<String, List<FoodConsumedDomain>>()
    private val cachedMonthKeys = mutableSetOf<String>()
    private var foodPendingDeletion: FoodConsumedDomain? = null

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
        perform {
            refreshMealTypes()
            loadMonth(selectedDay.value)
            foodsConsumed.value = foodsFromCache(selectedDay.value)
        }
        _state.value = LoadingState.loaded
        hasCompletedInitialLoad = true
    }

    suspend fun onRefresh() {
        if (!hasCompletedInitialLoad) return
        perform {
            advanceSelectedDayIfNeeded()
            refreshMealTypes()
            invalidateCache(selectedDay.value)
            loadMonth(selectedDay.value)
            foodsConsumed.value = foodsFromCache(selectedDay.value)
        }
    }

    suspend fun onFoodConsumedUpdated() {
        perform {
            invalidateCache(selectedDay.value)
            loadMonth(selectedDay.value)
            foodsConsumed.value = foodsFromCache(selectedDay.value)
        }
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
            invalidateCache(selectedDay.value)
            loadMonth(selectedDay.value)
            foodsConsumed.value = foodsFromCache(selectedDay.value)
        }
    }

    suspend fun onMealTypesChanged() {
        perform { refreshMealTypes() }
    }

    suspend fun onDayChanged(date: Instant) {
        isViewingToday = date.isSameDay(Instant.now())
        loadFoods(date)
    }

    suspend fun onDaySelected(date: Instant) {
        isViewingToday = date.isSameDay(Instant.now())
        selectedDay.value = date
        showCalendarSheet.value = false
        loadFoods(date)
    }

    suspend fun onCalendarMonthChanged(month: Instant) {
        if (monthCacheKey(month) in cachedMonthKeys) {
            _activeDaysInMonth.value = computeActiveDays(month)
        } else {
            perform { loadMonth(month) }
        }
    }

    // MARK: - Private

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

    private fun unknownErrorAlertItem(error: Throwable): AlertItem =
        if (error.isFirestoreUnreachable) {
            AlertItem(titleRes = R.string.common_error_offline, messageRes = R.string.common_error_offline_message)
        } else {
            AlertItem(titleRes = R.string.common_error_unknown, messageRes = R.string.common_error_unknown_message)
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
            _activeDaysInMonth.value = computeActiveDays(date)
        } else {
            perform {
                loadMonth(date)
                foodsConsumed.value = foodsFromCache(date)
            }
        }
    }

    private suspend fun loadMonth(date: Instant) {
        val foods = fetchFoodsConsumedForMonth(date)
        populateCache(foods, date)
        _activeDaysInMonth.value = computeActiveDays(date)
    }

    private fun monthCacheKey(date: Instant): String = date.formatCacheKey("yyyy-MM")

    private fun dayCacheKey(date: Instant): String = date.formatCacheKey("yyyy-MM-dd")

    private fun populateCache(foods: List<FoodConsumedDomain>, month: Instant) {
        val key = monthCacheKey(month)
        monthCache = monthCache.filterKeys { !it.startsWith(key) }.toMutableMap()
        cachedMonthKeys += key
        for ((dayKey, dayFoods) in foods.groupBy { dayCacheKey(it.date) }) {
            monthCache[dayKey] = monthCache[dayKey].orEmpty() + dayFoods
        }
    }

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
