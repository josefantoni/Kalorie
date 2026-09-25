package antoni.kalorie.features.dashboard

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.ScaledMacros
import antoni.kalorie.core.models.resolvedMealTypeId
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.AssignFoodMealTypeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMealTypesUseCaseProtocol
import antoni.kalorie.core.usecases.IsFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateFoodConsumedError
import antoni.kalorie.core.usecases.UpdateFoodConsumedUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.FavouriteToggling
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FoodConsumedDetailViewModel(
    food: FoodConsumedDomain,
    mealTypes: List<MealTypeDomain>,
    private val updateFoodConsumed: UpdateFoodConsumedUseCaseProtocol,
    private val assignFoodMealType: AssignFoodMealTypeUseCaseProtocol,
    private val fetchMealTypes: FetchMealTypesUseCaseProtocol,
    private val isFavouriteFood: IsFavouriteFoodUseCaseProtocol,
    private val addFavouriteFood: AddFavouriteFoodUseCaseProtocol,
    private val removeFavouriteFood: RemoveFavouriteFoodUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol,
    private val onFoodUpdated: () -> Unit,
) : ViewModel(), FavouriteToggling {

    // MARK: - Properties

    val weight = MutableStateFlow(food.weight)
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    private val _showCheckmark = MutableStateFlow(false)
    val showCheckmark: StateFlow<Boolean> = _showCheckmark
    override val alertItem = MutableStateFlow<AlertItem?>(null)
    override val isFavourite = MutableStateFlow(false)
    override val isTogglingFavourite = MutableStateFlow(false)
    private val _catalogueItem = MutableStateFlow<FoodItemDomain?>(null)
    val catalogueItem: StateFlow<FoodItemDomain?> = _catalogueItem
    private val _mealTypeId = MutableStateFlow(mealTypes.resolvedMealTypeId(food))
    val mealTypeId: StateFlow<String?> = _mealTypeId
    private val _mealTypes = MutableStateFlow(mealTypes)
    val mealTypes: StateFlow<List<MealTypeDomain>> = _mealTypes

    var food: FoodConsumedDomain = food
        private set

    private var savedWeight = food.weight
    private var didSelectMealType = false

    val scaledMacros: ScaledMacros
        get() = ScaledMacros(food = food, newWeight = weight.value)

    val hasWeightChanged: Boolean
        get() = weight.value != savedWeight

    val hasMealTypeChanged: Boolean
        get() = didSelectMealType && _mealTypeId.value != food.mealTypeId

    val hasChanges: Boolean
        get() = hasWeightChanged || hasMealTypeChanged

    val canShowFavouriteButton: Boolean
        get() = isFavourite.value || _catalogueItem.value != null

    val canToggleFavourite: Boolean
        get() = !isTogglingFavourite.value && canShowFavouriteButton

    // MARK: - Functions

    suspend fun onAppear() = coroutineScope {
        val favourite = async { loadIsFavourite() }
        val catalogueItem = async { loadCatalogueItem() }
        isFavourite.value = favourite.await()
        _catalogueItem.value = catalogueItem.await()
    }

    private suspend fun loadIsFavourite(): Boolean {
        if (food.foodItemKind == FoodItemKind.CREATED_MEAL) return false
        return try {
            isFavouriteFood(food.foodItemId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.DASHBOARD)
            false
        }
    }

    private suspend fun loadCatalogueItem(): FoodItemDomain? = try {
        when (food.foodItemKind) {
            FoodItemKind.CATALOGUE -> fetchFoodItemByBarcode(food.foodItemId)
            FoodItemKind.EXTERNAL -> fetchFoodByBarcodeExternally(food.foodItemId)
            FoodItemKind.CREATED_MEAL -> null
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.warning(error, Constants.LogCategory.DASHBOARD)
        null
    }

    suspend fun onFavouriteToggled() {
        toggleFavourite(
            item = _catalogueItem.value,
            removalId = food.foodItemId,
            addFavouriteFood = addFavouriteFood,
            removeFavouriteFood = removeFavouriteFood,
        )
    }

    fun onMealTypeSelected(mealTypeId: String) {
        didSelectMealType = true
        _mealTypeId.value = mealTypeId
    }

    suspend fun onSave() {
        if (_state.value.isLoading) return
        if (weight.value <= 0) {
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_invalidWeight)
            return
        }
        if (!hasChanges) return
        _state.value = LoadingState.Loading
        if (hasMealTypeChanged) {
            try {
                _mealTypes.value = fetchMealTypes()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
            val selectedMealTypeId = _mealTypeId.value
            if (selectedMealTypeId == null || _mealTypes.value.none { it.id == selectedMealTypeId }) {
                alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
                _state.value = LoadingState.loaded
                return
            }
        }
        val scaled = scaledMacros
        var hasPersistedWeight = false
        try {
            if (hasWeightChanged) {
                updateFoodConsumed(food, weight.value)
                food = food.withScaledWeight(weight.value, scaled)
                savedWeight = weight.value
                hasPersistedWeight = true
            }
            val selectedMealTypeId = _mealTypeId.value
            if (hasMealTypeChanged && selectedMealTypeId != null) {
                assignFoodMealType(food, selectedMealTypeId)
                food = food.withMealTypeId(selectedMealTypeId)
                didSelectMealType = false
            }
            onFoodUpdated()
            _state.value = LoadingState.loaded
            _showCheckmark.value = true
            delay(CHECKMARK_DURATION_MILLIS)
            _showCheckmark.value = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: UpdateFoodConsumedError.InvalidWeight) {
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_invalidWeight)
            _state.value = LoadingState.loaded
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DASHBOARD)
            if (hasPersistedWeight) onFoodUpdated()
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
            _state.value = LoadingState.loaded
        }
    }

    private companion object {
        const val CHECKMARK_DURATION_MILLIS = 2_000L
    }
}

private fun FoodConsumedDomain.withMealTypeId(mealTypeId: String?): FoodConsumedDomain = copy(mealTypeId = mealTypeId)

private fun FoodConsumedDomain.withScaledWeight(weight: Double, scaled: ScaledMacros): FoodConsumedDomain = copy(
    weight = weight,
    calories = scaled.calories,
    energyKJ = scaled.energyKJ,
    protein = scaled.protein,
    carbohydrate = scaled.carbohydrate,
    carbohydrateSugar = scaled.carbohydrateSugar,
    fat = scaled.fat,
    fatSaturated = scaled.fatSaturated,
    fatUnsaturated = scaled.fatUnsaturated,
    fiber = scaled.fiber,
    salt = scaled.salt,
)
