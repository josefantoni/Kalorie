package antoni.kalorie.features.addfoodsheet

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionError
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelImage
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteMyCreatedMealUseCaseProtocol
import antoni.kalorie.core.usecases.DeleteMySubmissionUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFavouriteFoodsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodByBarcodeExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMyCreatedMealsUseCaseProtocol
import antoni.kalorie.core.usecases.FetchMySubmissionsUseCaseProtocol
import antoni.kalorie.core.usecases.RefreshFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodExternallyUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.usecases.SubmitFoodItemUseCaseProtocol
import antoni.kalorie.core.usecases.UpdateMySubmissionUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.CameraAccess
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.NutritionLabelPrefilling
import antoni.kalorie.core.utils.isFirestoreUnreachable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AddFoodSheetMode(@StringRes val titleRes: Int) {
    SEARCH(R.string.addFood_mode_search),
    NEW_ITEM(R.string.addFood_mode_newItem),
    CREATE_MEAL(R.string.addFood_mode_createMeal),
}

class AddFoodSheetViewModel(
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
    private val submitFoodItem: SubmitFoodItemUseCaseProtocol,
    private val fetchMySubmissions: FetchMySubmissionsUseCaseProtocol,
    private val updateMySubmission: UpdateMySubmissionUseCaseProtocol,
    private val deleteMySubmission: DeleteMySubmissionUseCaseProtocol,
    private val searchFoodExternally: SearchFoodExternallyUseCaseProtocol,
    private val fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseProtocol,
    private val fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseProtocol,
    private val fetchFavouriteFoods: FetchFavouriteFoodsUseCaseProtocol,
    private val refreshFavouriteFood: RefreshFavouriteFoodUseCaseProtocol,
    private val fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseProtocol,
    private val deleteMyCreatedMeal: DeleteMyCreatedMealUseCaseProtocol,
    private val recognizeNutritionLabelUseCase: RecognizeNutritionLabelUseCaseProtocol,
    private val onFoodSaved: () -> Unit = {},
    isScannerVisible: Boolean = false,
) : ViewModel(), NutritionLabelPrefilling {

    // MARK: - Properties

    val localFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val externalFoodItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    private val _isExternalSearchLoading = MutableStateFlow(false)
    val isExternalSearchLoading: StateFlow<Boolean> = _isExternalSearchLoading
    val favouriteFoods = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    private val _favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds
    val searchText = MutableStateFlow("")
    private val _mode = MutableStateFlow(AddFoodSheetMode.SEARCH)
    val mode: StateFlow<AddFoodSheetMode> = _mode
    val myCreatedMeals = MutableStateFlow<List<MyCreatedMealDomain>>(emptyList())
    val isMealDeleteConfirmationVisible = MutableStateFlow(false)
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    override val formInput = MutableStateFlow(FoodItemFormInput())
    override val recognizedFields = MutableStateFlow<Set<FoodItemFormField>>(emptySet())
    override val isRecognizingNutritionLabel = MutableStateFlow(false)
    override val isNutritionLabelCameraVisible = MutableStateFlow(false)
    override val nutritionLabelCameraHintRes = MutableStateFlow<Int?>(null)
    val cameraAccess = MutableStateFlow(CameraAccess.NOT_DETERMINED)
    private var isNutritionLabelCameraReviewPending = false
    val isReviewPushed = MutableStateFlow(false)
    val mySubmissions = MutableStateFlow<List<FoodItemSubmissionDomain>>(emptyList())
    val isSubmissionConfirmationVisible = MutableStateFlow(false)
    private val _rejectionReasonBeingEdited = MutableStateFlow<String?>(null)
    val rejectionReasonBeingEdited: StateFlow<String?> = _rejectionReasonBeingEdited
    val isSubmissionDeleteConfirmationVisible = MutableStateFlow(false)
    val isMissingBarcodeConfirmationVisible = MutableStateFlow(false)
    val isBarcodeRescanVisible = MutableStateFlow(false)
    val rescannedBarcode = MutableStateFlow("")
    private var editingSubmissionId: String? = null
    private var submissionPendingDeletion: FoodItemSubmissionDomain? = null
    private var mealPendingDeletion: MyCreatedMealDomain? = null
    val isScannerVisible = MutableStateFlow(isScannerVisible)
    val lastScannedBarcode = MutableStateFlow("")
    private val _isBarcodeSearchLoading = MutableStateFlow(false)
    val isBarcodeSearchLoading: StateFlow<Boolean> = _isBarcodeSearchLoading
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val isPushedToQuantityView = MutableStateFlow(false)
    private val _selectedFoodItem = MutableStateFlow<FoodItemDomain?>(null)
    val selectedFoodItem: StateFlow<FoodItemDomain?> = _selectedFoodItem
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss
    @StringRes val searchExampleRes: Int = searchExamples.random()

    val isEditingSubmission: Boolean
        get() = editingSubmissionId != null

    val displayedResults: List<FoodItemDomain>
        get() {
            val query = searchText.value.lowercase()
            if (query.isEmpty()) return localFoodItems.value
            val matchingFavourites = favouriteFoods.value.filter {
                it.czName.lowercase().startsWith(query) || it.engName.lowercase().startsWith(query)
            }
            val favouriteIds = matchingFavourites.map { it.id }.toSet()
            val matchingMeals = myCreatedMeals.value
                .map { it.asFoodItem() }
                .filter { it.czName.lowercase().startsWith(query) && it.id !in favouriteIds }
            val seenSubmissionIds = (favouriteIds + matchingMeals.map { it.id }).toMutableSet()
            val matchingSubmissions = mySubmissions.value
                .map { it.item }
                .filter { it.czName.lowercase().startsWith(query) || it.engName.lowercase().startsWith(query) }
                .filter { seenSubmissionIds.add(it.id) }
            val matchingIds = favouriteIds + matchingMeals.map { it.id } + matchingSubmissions.map { it.id }
            return matchingFavourites + matchingMeals + matchingSubmissions + localFoodItems.value.filter { it.id !in matchingIds }
        }

    // MARK: - Functions

    fun onScannerButtonTapped() {
        _mode.value = AddFoodSheetMode.SEARCH
        isScannerVisible.value = true
    }

    fun onModeSelected(mode: AddFoodSheetMode) {
        if (mode == _mode.value) return
        _mode.value = mode
        isScannerVisible.value = false
        isReviewPushed.value = false
        if (mode != AddFoodSheetMode.NEW_ITEM) return
        startFreshNewItem()
    }

    fun onNutritionLabelPromptTapped() {
        startFreshNewItem()
        cameraAccess.value = CameraAccess.AUTHORIZED
        isNutritionLabelCameraVisible.value = true
    }

    fun onNutritionLabelCameraAccessDenied() {
        startFreshNewItem()
        cameraAccess.value = CameraAccess.DENIED
    }

    fun onAddManuallyTapped() {
        startFreshNewItem()
        isReviewPushed.value = true
    }

    suspend fun onNutritionLabelCaptured(image: NutritionLabelImage, liveBarcode: String?) {
        val succeeded = recognizeNutritionLabel(image, liveBarcode, recognizeNutritionLabelUseCase)
        if (succeeded) isNutritionLabelCameraReviewPending = true
    }

    fun onNutritionLabelCameraDismissed() {
        if (!isNutritionLabelCameraReviewPending) return
        isNutritionLabelCameraReviewPending = false
        isReviewPushed.value = true
    }

    fun onBarcodeRescanTapped() {
        isBarcodeRescanVisible.value = true
    }

    fun onBarcodeRescanned() {
        if (rescannedBarcode.value.isEmpty()) return
        formInput.value = formInput.value.copy(scannedCode = rescannedBarcode.value)
        rescannedBarcode.value = ""
        isBarcodeRescanVisible.value = false
    }

    fun submissionStatus(item: FoodItemDomain): FoodItemSubmissionStatus? =
        mySubmissions.value.firstOrNull { it.item.id == item.id }?.status

    fun onSelectRejectedSubmission(item: FoodItemDomain) {
        val submission = mySubmissions.value.firstOrNull { it.item.id == item.id } ?: return
        openEditingForm(submission)
    }

    fun onSelectSubmission(submission: FoodItemSubmissionDomain) {
        if (submission.status != FoodItemSubmissionStatus.REJECTED) {
            onSelectFoodItem(submission.item)
            return
        }
        openEditingForm(submission)
    }

    fun onSubmissionConfirmationDismissed() {
        isSubmissionConfirmationVisible.value = false
        _shouldDismiss.value = true
    }

    fun onDeleteSubmissionRequested(submission: FoodItemSubmissionDomain) {
        submissionPendingDeletion = submission
        isSubmissionDeleteConfirmationVisible.value = true
    }

    suspend fun onDeleteSubmissionConfirmed() {
        val submission = submissionPendingDeletion ?: return
        submissionPendingDeletion = null
        val index = mySubmissions.value.indexOfFirst { it.id == submission.id }
        mySubmissions.value = mySubmissions.value.filter { it.id != submission.id }
        try {
            deleteMySubmission(submission.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ADD_FOOD_SHEET)
            if (index >= 0) {
                mySubmissions.value = mySubmissions.value.toMutableList().also { it.add(minOf(index, it.size), submission) }
            }
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_withdrawSubmissionFailed)
        }
    }

    suspend fun onCreateFoodItem() {
        if (formInput.value.scannedCode.isNotEmpty()) {
            writeFoodItem()
            return
        }
        isMissingBarcodeConfirmationVisible.value = true
    }

    suspend fun onMissingBarcodeConfirmed() {
        isMissingBarcodeConfirmationVisible.value = false
        writeFoodItem()
    }

    private suspend fun writeFoodItem() {
        _state.value = LoadingState.Loading
        val item = formInput.value.asFoodItemDomain()
        try {
            val submissionId = editingSubmissionId
            if (submissionId != null) {
                updateMySubmission(submissionId, item)
            } else {
                submitFoodItem(item)
            }
            editingSubmissionId = null
            _rejectionReasonBeingEdited.value = null
            isSubmissionConfirmationVisible.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ADD_FOOD_SHEET)
            alertItem.value = when {
                error.isFirestoreUnreachable ->
                    AlertItem(titleRes = R.string.common_error_offline, messageRes = R.string.common_error_offline_message)
                error is FoodItemSubmissionError.InvalidCode -> AlertItem(titleRes = R.string.addFood_error_invalidCode)
                error is FoodItemSubmissionError.InvalidName -> AlertItem(titleRes = R.string.addFood_error_invalidName)
                error is FoodItemSubmissionError.InvalidCalories -> AlertItem(titleRes = R.string.addFood_error_invalidCalories)
                error is FoodItemSubmissionError.InvalidWeight -> AlertItem(titleRes = R.string.addFood_error_invalidWeight)
                error is FoodItemSubmissionError.InvalidPortion -> AlertItem(titleRes = error.error.alertTitleRes)
                error is FoodItemSubmissionError.ItemAlreadyExists -> AlertItem(titleRes = R.string.addFood_error_itemAlreadyExists)
                else -> AlertItem(titleRes = R.string.common_error_unknown)
            }
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    private fun startFreshNewItem() {
        formInput.value = FoodItemFormInput()
        recognizedFields.value = emptySet()
        editingSubmissionId = null
        _rejectionReasonBeingEdited.value = null
    }

    private fun openEditingForm(submission: FoodItemSubmissionDomain) {
        formInput.value = FoodItemFormInput.from(submission.item)
        recognizedFields.value = emptySet()
        editingSubmissionId = submission.id
        _rejectionReasonBeingEdited.value = submission.rejectReason
        _mode.value = AddFoodSheetMode.NEW_ITEM
        isReviewPushed.value = true
    }

    fun onDeleteMealRequested(meal: MyCreatedMealDomain) {
        mealPendingDeletion = meal
        isMealDeleteConfirmationVisible.value = true
    }

    suspend fun onDeleteMealConfirmed() {
        val meal = mealPendingDeletion ?: return
        mealPendingDeletion = null
        onDeleteMealConfirmed(meal)
    }

    suspend fun onDeleteMealConfirmed(meal: MyCreatedMealDomain) {
        isPushedToQuantityView.value = false
        val index = myCreatedMeals.value.indexOfFirst { it.id == meal.id }
        myCreatedMeals.value = myCreatedMeals.value.filter { it.id != meal.id }
        try {
            deleteMyCreatedMeal(meal.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ADD_FOOD_SHEET)
            if (index >= 0) {
                myCreatedMeals.value = myCreatedMeals.value.toMutableList().also { it.add(minOf(index, it.size), meal) }
            }
            alertItem.value = AlertItem(titleRes = R.string.myCreatedMeal_error_deleteFailed)
        }
    }

    suspend fun onMyCreatedMealSaved() {
        _mode.value = AddFoodSheetMode.SEARCH
        isPushedToQuantityView.value = false
        try {
            myCreatedMeals.value = fetchMyCreatedMeals()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
        }
    }

    fun isMyCreatedMeal(item: FoodItemDomain): Boolean = item.kind == FoodItemKind.CREATED_MEAL

    fun myCreatedMeal(item: FoodItemDomain): MyCreatedMealDomain? {
        if (!isMyCreatedMeal(item)) return null
        return myCreatedMeals.value.firstOrNull { it.id == item.id }
    }

    fun onMyCreatedMealUpdated(meal: MyCreatedMealDomain) {
        val index = myCreatedMeals.value.indexOfFirst { it.id == meal.id }
        if (index < 0) return
        myCreatedMeals.value = myCreatedMeals.value.toMutableList().also { it[index] = meal }
    }

    fun onScenePhaseActive(isCameraAvailable: Boolean) {
        cameraAccess.value = when {
            isCameraAvailable -> CameraAccess.AUTHORIZED
            cameraAccess.value == CameraAccess.AUTHORIZED -> CameraAccess.DENIED
            else -> cameraAccess.value
        }
        if (isNutritionLabelCameraVisible.value && !isCameraAvailable) isNutritionLabelCameraVisible.value = false
        if (!isScannerVisible.value || isCameraAvailable) return
        isScannerVisible.value = false
        alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert)
    }

    suspend fun onBarcodeScanned() {
        val barcode = lastScannedBarcode.value
        if (barcode.isEmpty()) return
        _isBarcodeSearchLoading.value = true
        try {
            try {
                fetchFoodItemByBarcode(barcode)?.let { local ->
                    isScannerVisible.value = false
                    onSelectFoodItem(local)
                    return
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
            }
            try {
                fetchFoodByBarcodeExternally(barcode)?.let { external ->
                    isScannerVisible.value = false
                    onSelectFoodItem(external)
                    return
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.ADD_FOOD_SHEET)
                alertItem.value = AlertItem(titleRes = R.string.addFood_error_loadFailed)
                return
            }
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_barcodeNotFound)
        } finally {
            lastScannedBarcode.value = ""
            _isBarcodeSearchLoading.value = false
        }
    }

    suspend fun onSearchTextChanged() {
        if (isPushedToQuantityView.value) return
        if (searchText.value.isEmpty()) {
            localFoodItems.value = emptyList()
            externalFoodItems.value = emptyList()
            return
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        try {
            localFoodItems.value = searchFoodItems(searchText.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
            localFoodItems.value = emptyList()
            externalFoodItems.value = emptyList()
            return
        }
        if (displayedResults.isNotEmpty() || searchText.value.length < EXTERNAL_SEARCH_MIN_LENGTH) {
            externalFoodItems.value = emptyList()
            return
        }
        _isExternalSearchLoading.value = true
        try {
            externalFoodItems.value = searchFoodExternally(searchText.value)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
            externalFoodItems.value = emptyList()
        } finally {
            _isExternalSearchLoading.value = false
        }
    }

    fun onSelectFoodItem(item: FoodItemDomain) {
        if (isPushedToQuantityView.value) return
        _selectedFoodItem.value = item
        isPushedToQuantityView.value = true
    }

    suspend fun onSelectFavouriteFood(item: FoodItemDomain) {
        if (isPushedToQuantityView.value) return
        var resolved = item
        try {
            resolved = refreshFavouriteFood(item)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
        }
        val index = favouriteFoods.value.indexOfFirst { it.id == item.id }
        if (resolved != item && index >= 0) {
            favouriteFoods.value = favouriteFoods.value.toMutableList().also { it[index] = resolved }
        }
        onSelectFoodItem(resolved)
    }

    suspend fun onAppear() {
        coroutineScope {            val favourites = async {
                try {
                    fetchFavouriteFoods()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
                    null
                }
            }
            val submissions = async {
            try {
                fetchMySubmissions()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
                null
            }
        }
        val meals = async {
                try {
                    fetchMyCreatedMeals()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warning(error, Constants.LogCategory.ADD_FOOD_SHEET)
                    null
                }
            }
            favourites.await()?.let { items ->
                favouriteFoods.value = items
                _favouriteIds.value = items.map { it.id }.toSet()
            }
            meals.await()?.let { myCreatedMeals.value = it }
            submissions.await()?.let { mySubmissions.value = it }
        }
    }

    fun isFavourite(item: FoodItemDomain): Boolean = item.id in _favouriteIds.value

    fun onFavouriteChanged(id: String, isFavourite: Boolean, item: FoodItemDomain) {
        favouriteFoods.value = favouriteFoods.value.filter { it.id != id }
        if (isFavourite) {
            _favouriteIds.value = _favouriteIds.value + id
            favouriteFoods.value = listOf(item) + favouriteFoods.value
        } else {
            _favouriteIds.value = _favouriteIds.value - id
        }
    }

    fun onFoodConsumedSaved() {
        onFoodSaved()
        _shouldDismiss.value = true
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
        private const val EXTERNAL_SEARCH_MIN_LENGTH = 3

        val searchExamples = listOf(
            R.string.addFood_search_example_01,
            R.string.addFood_search_example_02,
            R.string.addFood_search_example_03,
            R.string.addFood_search_example_04,
            R.string.addFood_search_example_05,
            R.string.addFood_search_example_06,
            R.string.addFood_search_example_07,
            R.string.addFood_search_example_08,
        )
    }
}
