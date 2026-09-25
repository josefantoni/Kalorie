package antoni.kalorie.features.mycreatedmeal

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.components.FoodPortionDraft
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.models.MyCreatedMealValidation
import antoni.kalorie.core.usecases.CreateMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemsByIdsUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.addfoodsheet.AddFoodSheetViewModel
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MyCreatedMealIngredientDraft(
    val id: UUID = UUID.randomUUID(),
    val item: FoodItemDomain,
    val gramsText: String,
)

class MyCreatedMealEditorViewModel(
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
    private val searchFoodExternally: SearchFoodExternallyUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol,
    private val fetchFoodItemsByIds: FetchFoodItemsByIdsUseCaseProtocol,
    private val createMyCreatedMeal: CreateMyCreatedMealUseCaseProtocol,
    private val updateMyCreatedMeal: UpdateMyCreatedMealUseCaseProtocol,
    private val existingMeal: MyCreatedMealDomain? = null,
    private val onSaved: () -> Unit = {},
    private val dismissesOnSave: Boolean = true,
) : ViewModel() {

    // MARK: - Properties

    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    val name = MutableStateFlow(existingMeal?.name ?: "")
    val ingredients = MutableStateFlow(
        (existingMeal?.ingredients ?: emptyList()).map { ingredient ->
            MyCreatedMealIngredientDraft(
                item = FoodItemDomain(
                    id = ingredient.foodItemId,
                    kind = FoodItemKind.CATALOGUE,
                    czName = ingredient.czName,
                    engName = ingredient.engName,
                    weight = ingredient.grams,
                    date = Instant.now(),
                    nutrition = ingredient.nutrition,
                ),
                gramsText = formattedGrams(ingredient.grams),
            )
        },
    )
    val portions = MutableStateFlow(
        (existingMeal?.portions ?: emptyList()).ifEmpty { null }
            ?.map { FoodPortionDraft(name = it.name, gramsText = formattedGrams(it.grams)) }
            ?: listOf(FoodPortionDraft.blank),
    )
    val searchText = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val searchResults: StateFlow<List<FoodItemDomain>> = _searchResults
    private val _externalSearchResults = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val externalSearchResults: StateFlow<List<FoodItemDomain>> = _externalSearchResults
    private val _isExternalSearchLoading = MutableStateFlow(false)
    val isExternalSearchLoading: StateFlow<Boolean> = _isExternalSearchLoading
    val isScannerVisible = MutableStateFlow(false)
    val lastScannedBarcode = MutableStateFlow("")
    private val _isBarcodeSearchLoading = MutableStateFlow(false)
    val isBarcodeSearchLoading: StateFlow<Boolean> = _isBarcodeSearchLoading
    private val _scannedIngredientId = MutableStateFlow<UUID?>(null)
    val scannedIngredientId: StateFlow<UUID?> = _scannedIngredientId
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val isSaveConfirmationVisible = MutableStateFlow(false)
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss
    @StringRes val searchExampleRes: Int = AddFoodSheetViewModel.searchExamples.random()

    private val initialName = name.value
    private val initialIngredients = ingredientDomains(ingredients.value)
    private val initialPortions = parsedPortions(portions.value)

    val isEditing: Boolean
        get() = existingMeal != null

    @get:StringRes
    val titleRes: Int
        get() = if (isEditing) R.string.myCreatedMeal_title_edit else R.string.myCreatedMeal_title_new

    @get:StringRes
    val confirmationTitleRes: Int
        get() = if (isEditing) R.string.myCreatedMeal_confirm_update else R.string.myCreatedMeal_confirm_create

    val ingredientDomains: List<MyCreatedMealIngredientDomain>
        get() = ingredientDomains(ingredients.value)

    val portionDomains: List<FoodPortionDomain>
        get() = parsedPortions(portions.value)

    val hasChanges: Boolean
        get() = name.value != initialName || ingredientDomains != initialIngredients || portionDomains != initialPortions

    val canSave: Boolean
        get() {
            if (!MyCreatedMealValidation.canSave(name = name.value, ingredients = ingredientDomains, portions = portionDomains)) return false
            return !isEditing || hasChanges
        }

    // MARK: - Functions

    suspend fun onAppear() {
        if (!isEditing) return
        val freshItems = try {
            fetchFoodItemsByIds(ingredients.value.map { it.item.id })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MY_CREATED_MEAL)
            return
        }
        val freshById = freshItems.associateBy { it.id }
        ingredients.value = ingredients.value.map { draft ->
            val fresh = freshById[draft.item.id]
            if (
                fresh != null &&
                (
                    fresh.nutrition != draft.item.nutrition ||
                        fresh.czName != draft.item.czName ||
                        fresh.engName != draft.item.engName
                    )
            ) {
                draft.copy(item = fresh)
            } else {
                draft
            }
        }
    }

    suspend fun onSearchTextChanged() {
        if (searchText.value.isEmpty()) {
            _searchResults.value = emptyList()
            _externalSearchResults.value = emptyList()
            return
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        try {
            _searchResults.value = searchFoodItems(searchText.value)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _searchResults.value = emptyList()
        }
        if (_searchResults.value.isNotEmpty() || searchText.value.length < EXTERNAL_SEARCH_MIN_LENGTH) {
            _externalSearchResults.value = emptyList()
            return
        }
        _isExternalSearchLoading.value = true
        try {
            _externalSearchResults.value = searchFoodExternally(searchText.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MY_CREATED_MEAL)
            _externalSearchResults.value = emptyList()
        } finally {
            _isExternalSearchLoading.value = false
        }
    }

    fun onScannerButtonTapped() {
        isScannerVisible.value = true
    }

    suspend fun onBarcodeScanned() {
        val barcode = lastScannedBarcode.value
        if (barcode.isEmpty()) return
        _isBarcodeSearchLoading.value = true
        try {
            try {
                fetchFoodItemByBarcode(barcode)?.let { local ->
                    isScannerVisible.value = false
                    _scannedIngredientId.value = onSelectSearchResult(local)
                    return
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.MY_CREATED_MEAL)
            }
            try {
                fetchFoodByBarcodeExternally(barcode)?.let { external ->
                    isScannerVisible.value = false
                    _scannedIngredientId.value = onSelectSearchResult(external)
                    return
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.MY_CREATED_MEAL)
                alertItem.value = AlertItem(titleRes = R.string.addFood_error_loadFailed)
                return
            }
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_barcodeNotFound)
        } finally {
            lastScannedBarcode.value = ""
            _isBarcodeSearchLoading.value = false
        }
    }

    fun onSelectSearchResult(item: FoodItemDomain): UUID {
        val draft = MyCreatedMealIngredientDraft(item = item, gramsText = "")
        ingredients.value = ingredients.value + draft
        searchText.value = ""
        _searchResults.value = emptyList()
        _externalSearchResults.value = emptyList()
        return draft.id
    }

    fun onGramsFieldDefocused(id: UUID) {
        val index = ingredients.value.indexOfFirst { it.id == id }
        if (index < 0 || ingredients.value[index].gramsText.isNotEmpty()) return
        ingredients.value = ingredients.value.toMutableList().also { it[index] = it[index].copy(gramsText = "100") }
    }

    fun onDeleteIngredient(offsets: Set<Int>) {
        ingredients.value = ingredients.value.filterIndexed { index, _ -> index !in offsets }
    }

    fun onSaveTapped() {
        if (!canSave) return
        isSaveConfirmationVisible.value = true
    }

    suspend fun onSaveConfirmed() {
        if (_state.value.isLoading) return
        _state.value = LoadingState.Loading
        try {
            val meal = existingMeal
            if (meal != null) {
                updateMyCreatedMeal(
                    MyCreatedMealDomain(
                        id = meal.id,
                        name = name.value,
                        ingredients = ingredientDomains,
                        createdAt = meal.createdAt,
                        updatedAt = meal.updatedAt,
                        portions = portionDomains,
                    ),
                )
            } else {
                createMyCreatedMeal(name.value, ingredientDomains, portionDomains)
            }
            onSaved()
            _shouldDismiss.value = dismissesOnSave
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MY_CREATED_MEAL)
            alertItem.value = AlertItem(titleRes = R.string.myCreatedMeal_error_saveFailed)
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    // MARK: - Private

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val EXTERNAL_SEARCH_MIN_LENGTH = 3

        fun ingredientDomains(ingredients: List<MyCreatedMealIngredientDraft>): List<MyCreatedMealIngredientDomain> =
            ingredients.map { draft ->
                MyCreatedMealIngredientDomain(
                    foodItemId = draft.item.id,
                    czName = draft.item.czName,
                    engName = draft.item.engName,
                    grams = parsedGrams(draft.gramsText),
                    nutrition = draft.item.nutrition,
                )
            }

        fun parsedGrams(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: 0.0

        fun parsedPortions(drafts: List<FoodPortionDraft>): List<FoodPortionDomain> =
            drafts.mapNotNull { draft ->
                val grams = parsedGrams(draft.gramsText)
                if (grams >= 1) FoodPortionDomain(name = draft.name, grams = grams) else null
            }

        fun formattedGrams(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}
