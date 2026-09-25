package antoni.kalorie.features.foodquantity

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.components.FoodPortionDraft
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.FoodPortionError
import antoni.kalorie.core.models.FoodPortionValidation
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.ScaledMacros
import antoni.kalorie.core.models.mealType
import antoni.kalorie.core.models.scaled
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemPersonalPortionsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SaveFoodConsumedUseCaseProtocol
import antoni.kalorie.core.usecases.SaveFoodItemPersonalPortionsUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.FavouriteToggling
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.isLoading
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    isFavourite: Boolean,
    private val addFavouriteFood: AddFavouriteFoodUseCaseProtocol,
    private val removeFavouriteFood: RemoveFavouriteFoodUseCaseProtocol,
    private val fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseProtocol,
    private val saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseProtocol,
    private var meal: MyCreatedMealDomain?,
    private val updateMyCreatedMeal: UpdateMyCreatedMealUseCaseProtocol,
    private val onSaved: () -> Unit,
    private val onMealUpdated: (MyCreatedMealDomain) -> Unit,
    private val onFavouriteChanged: (String, Boolean) -> Unit,
    quantity: Double = 1.0,
    unit: FoodQuantityUnit = FoodQuantityUnit.HundredGrams,
) : ViewModel(), FavouriteToggling {

    // MARK: - Properties

    val unit = MutableStateFlow(unit)
    val quantity = MutableStateFlow(quantity)
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    override val alertItem = MutableStateFlow<AlertItem?>(null)
    override val isFavourite = MutableStateFlow(isFavourite)
    override val isTogglingFavourite = MutableStateFlow(false)
    private val _mealTypes = MutableStateFlow(mealTypes)
    val mealTypes: StateFlow<List<MealTypeDomain>> = _mealTypes
    val selectedMealTypeId = MutableStateFlow(mealTypes.mealType(selectedDate)?.id)
    private val _personalPortions = MutableStateFlow(meal?.portions ?: emptyList())
    val personalPortions: StateFlow<List<FoodPortionDomain>> = _personalPortions
    val isPersonalPortionsManagerPushed = MutableStateFlow(false)
    val portionDrafts = MutableStateFlow(listOf(FoodPortionDraft.blank))
    private val _showPortionCheckmark = MutableStateFlow(false)
    val showPortionCheckmark: StateFlow<Boolean> = _showPortionCheckmark
    private var hasUserSelectedUnit = false
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

    val arePortionDraftsComplete: Boolean
        get() = portionDrafts.value.all { it.isComplete }

    val canSavePortionDrafts: Boolean
        get() = portionDrafts.value.any { it.isComplete }

    val isPersonalPortionsAvailable: Boolean
        get() = item.kind == FoodItemKind.CATALOGUE || meal != null

    val unitOptions: List<FoodQuantityUnit>
        get() = (_personalPortions.value + (if (meal == null) item.portions else emptyList())).map { FoodQuantityUnit.Portion(it) } +
            listOf(FoodQuantityUnit.Grams, FoodQuantityUnit.HundredGrams)

    // MARK: - Functions

    suspend fun onAppear() {
        if (item.kind != FoodItemKind.CATALOGUE) return
        try {
            _personalPortions.value = fetchFoodItemPersonalPortions(item.id)
            val firstPersonalPortion = _personalPortions.value.firstOrNull()
            if (!hasUserSelectedUnit && firstPersonalPortion != null) {
                quantity.value = 1.0
                unit.value = FoodQuantityUnit.Portion(firstPersonalPortion)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.FOOD_QUANTITY)
        }
    }

    fun onUnitSelected(newUnit: FoodQuantityUnit) {
        hasUserSelectedUnit = true
        val currentGrams = quantity.value * unit.value.gramsPerUnit
        unit.value = newUnit
        quantity.value = currentGrams / newUnit.gramsPerUnit
    }

    fun onMealTypeSelected(mealTypeId: String) {
        hasUserSelectedMealType = true
        selectedMealTypeId.value = mealTypeId
    }

    fun onPortionsManagerOpened() {
        portionDrafts.value = listOf(FoodPortionDraft.blank)
    }

    fun onAddPortionDraftTapped() {
        portionDrafts.value = portionDrafts.value + FoodPortionDraft.blank
    }

    fun onDeletePortionDraft(draft: FoodPortionDraft) {
        portionDrafts.value = portionDrafts.value.filter { it.id != draft.id }.ifEmpty { listOf(FoodPortionDraft.blank) }
    }

    suspend fun onSavePersonalPortions() {
        val filledDrafts = portionDrafts.value.filter { it.name.isNotBlank() || it.gramsText.isNotEmpty() }
        if (filledDrafts.isEmpty()) return
        val newPortions = mutableListOf<FoodPortionDomain>()
        for (draft in filledDrafts) {
            val grams = draft.gramsText.replace(',', '.').toDoubleOrNull() ?: 0.0
            val error = FoodPortionValidation.validate(name = draft.name, grams = grams)
            if (error != null) {
                alertItem.value = AlertItem(titleRes = error.alertTitleRes)
                return
            }
            newPortions.add(FoodPortionDomain(name = draft.name, grams = grams))
        }
        val original = _personalPortions.value
        _personalPortions.value = original + newPortions
        try {
            persistPersonalPortions()
            portionDrafts.value = listOf(FoodPortionDraft.blank)
            _showPortionCheckmark.value = true
            delay(CHECKMARK_DURATION_MILLIS)
            _showPortionCheckmark.value = false
        } catch (error: CancellationException) {
            throw error
        } catch (error: FoodPortionError) {
            Log.error(error, Constants.LogCategory.FOOD_QUANTITY)
            _personalPortions.value = original
            alertItem.value = AlertItem(titleRes = error.alertTitleRes)
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.FOOD_QUANTITY)
            _personalPortions.value = original
            alertItem.value = AlertItem(titleRes = R.string.myPortions_error_saveFailed)
        }
    }

    suspend fun onDeletePersonalPortion(portion: FoodPortionDomain) {
        val original = _personalPortions.value
        val originalUnit = unit.value
        val originalQuantity = quantity.value
        _personalPortions.value = original.filter { it != portion }
        if (unitOptions.none { it == unit.value }) {
            quantity.value = grams
            unit.value = FoodQuantityUnit.Grams
        }
        try {
            persistPersonalPortions()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.FOOD_QUANTITY)
            _personalPortions.value = original
            unit.value = originalUnit
            quantity.value = originalQuantity
            alertItem.value = AlertItem(titleRes = R.string.myPortions_error_deleteFailed)
        }
    }

    private suspend fun persistPersonalPortions() {
        val currentMeal = meal
        if (currentMeal == null) {
            saveFoodItemPersonalPortions(item.id, _personalPortions.value)
            return
        }
        val updated = currentMeal.copy(portions = _personalPortions.value)
        updateMyCreatedMeal(updated)
        meal = updated
        onMealUpdated(updated)
    }

    suspend fun onFavouriteToggled() {
        val itemId = item.id
        toggleFavourite(
            item = item,
            removalId = itemId,
            addFavouriteFood = addFavouriteFood,
            removeFavouriteFood = removeFavouriteFood,
        ) { newValue -> onFavouriteChanged(itemId, newValue) }
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
        private const val CHECKMARK_DURATION_MILLIS = 2_000L

        fun defaultUnit(item: FoodItemDomain): FoodQuantityUnit =
            item.portions.firstOrNull()?.let { FoodQuantityUnit.Portion(it) } ?: FoodQuantityUnit.Grams
    }
}
