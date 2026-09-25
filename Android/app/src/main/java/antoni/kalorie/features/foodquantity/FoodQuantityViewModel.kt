package antoni.kalorie.features.foodquantity

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.ScaledMacros
import antoni.kalorie.core.models.mealType
import antoni.kalorie.core.models.scaled
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.isLoading
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface FoodQuantityUnit {
    data object HundredGrams : FoodQuantityUnit
    data object Grams : FoodQuantityUnit
    data class Portion(val portion: FoodPortionDomain) : FoodQuantityUnit

    // MARK: - Properties

    val gramsPerUnit: Double
        get() = when (this) {
            HundredGrams -> 100.0
            Grams -> 1.0
            is Portion -> portion.grams
        }
}

class FoodQuantityViewModel(
    val item: FoodItemDomain,
    private val saveFoodConsumed: SaveFoodConsumedUseCaseProtocol,
    private val fetchMealTypes: FetchMealTypesUseCaseProtocol,
    private val selectedDate: Instant,
    mealTypes: List<MealTypeDomain>,
    private val onSaved: () -> Unit,
    quantity: Double = 1.0,
    unit: FoodQuantityUnit = FoodQuantityUnit.HundredGrams,
) : ViewModel() {

    // MARK: - Properties

    val unit = MutableStateFlow(unit)
    val quantity = MutableStateFlow(quantity)
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    val alertItem = MutableStateFlow<AlertItem?>(null)
    private val _mealTypes = MutableStateFlow(mealTypes)
    val mealTypes: StateFlow<List<MealTypeDomain>> = _mealTypes
    val selectedMealTypeId = MutableStateFlow(mealTypes.mealType(selectedDate)?.id)
    private var hasUserSelectedMealType = false

    val grams: Double
        get() = quantity.value * unit.value.gramsPerUnit

    private val scaledMacros: ScaledMacros
        get() = item.scaled(toGrams = grams)

    val scaledCalories: Int get() = scaledMacros.calories
    val scaledProtein: Double get() = scaledMacros.protein
    val scaledCarbohydrate: Double get() = scaledMacros.carbohydrate
    val scaledCarbohydrateSugar: Double get() = scaledMacros.carbohydrateSugar
    val scaledFat: Double get() = scaledMacros.fat
    val scaledFatSaturated: Double? get() = scaledMacros.fatSaturated
    val scaledFiber: Double? get() = scaledMacros.fiber
    val scaledSalt: Double get() = scaledMacros.salt

    val unitOptions: List<FoodQuantityUnit>
        get() = item.portions.map { FoodQuantityUnit.Portion(it) } + listOf(FoodQuantityUnit.Grams, FoodQuantityUnit.HundredGrams)

    // MARK: - Functions

    fun onUnitSelected(newUnit: FoodQuantityUnit) {
        val currentGrams = quantity.value * unit.value.gramsPerUnit
        unit.value = newUnit
        quantity.value = currentGrams / newUnit.gramsPerUnit
    }

    fun onMealTypeSelected(mealTypeId: String) {
        hasUserSelectedMealType = true
        selectedMealTypeId.value = mealTypeId
    }

    suspend fun onConfirm() {
        if (_state.value.isLoading) return
        if (grams <= 0) {
            alertItem.value = AlertItem(titleRes = R.string.foodQuantity_error_invalidQuantity)
            return
        }
        _state.value = LoadingState.Loading
        try {
            try {
                _mealTypes.value = fetchMealTypes()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.FOOD_QUANTITY)
            }
            val mealTypeId: String?
            if (hasUserSelectedMealType) {
                val selectedId = selectedMealTypeId.value
                if (selectedId == null || _mealTypes.value.none { it.id == selectedId }) {
                    alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
                    return
                }
                mealTypeId = selectedId
            } else {
                mealTypeId = _mealTypes.value.mealType(selectedDate)?.id
            }
            saveFoodConsumed(item, grams = grams, date = selectedDate, mealTypeId = mealTypeId)
            onSaved()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.FOOD_QUANTITY)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    companion object {
        fun defaultUnit(item: FoodItemDomain): FoodQuantityUnit =
            item.portions.firstOrNull()?.let { FoodQuantityUnit.Portion(it) } ?: FoodQuantityUnit.Grams
    }
}
