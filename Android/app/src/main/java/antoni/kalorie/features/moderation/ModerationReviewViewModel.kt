package antoni.kalorie.features.moderation

import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelImage
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseProtocol
import antoni.kalorie.core.usecases.ApproveSubmissionError
import antoni.kalorie.core.usecases.ApproveSubmissionUseCaseProtocol
import antoni.kalorie.core.usecases.CreateFoodItemError
import antoni.kalorie.core.usecases.RejectSubmissionError
import antoni.kalorie.core.usecases.RejectSubmissionUseCaseProtocol
import antoni.kalorie.core.usecases.SearchFoodItemsUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.Log
import antoni.kalorie.core.utils.NutritionLabelPrefilling
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.addfoodsheet.FoodItemFormInput
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModerationReviewViewModel(
    private val submission: FoodItemSubmissionDomain,
    private val approveSubmission: ApproveSubmissionUseCaseProtocol,
    private val rejectSubmission: RejectSubmissionUseCaseProtocol,
    private val searchFoodItems: SearchFoodItemsUseCaseProtocol,
    private val recognizeNutritionLabelUseCase: RecognizeNutritionLabelUseCaseProtocol,
    private val onResolved: () -> Unit,
) : ViewModel(), NutritionLabelPrefilling {

    // MARK: - Properties

    override val formInput = MutableStateFlow(FoodItemFormInput.from(submission.item))
    override val recognizedFields = MutableStateFlow<Set<FoodItemFormField>>(emptySet())
    override val isRecognizingNutritionLabel = MutableStateFlow(false)
    override val isNutritionLabelCameraVisible = MutableStateFlow(false)
    override val nutritionLabelCameraHintRes = MutableStateFlow<Int?>(null)
    private val _state = MutableStateFlow<LoadingState<Unit>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<Unit>> = _state
    val alertItem = MutableStateFlow<AlertItem?>(null)
    val isRejectSheetVisible = MutableStateFlow(false)
    val rejectReason = MutableStateFlow("")
    private val _shouldDismiss = MutableStateFlow(false)
    val shouldDismiss: StateFlow<Boolean> = _shouldDismiss
    private val _similarCatalogueItems = MutableStateFlow<List<FoodItemDomain>>(emptyList())
    val similarCatalogueItems: StateFlow<List<FoodItemDomain>> = _similarCatalogueItems
    private val _isSimilarCatalogueItemsSectionAvailable = MutableStateFlow(true)
    val isSimilarCatalogueItemsSectionAvailable: StateFlow<Boolean> = _isSimilarCatalogueItemsSectionAvailable

    val submittedAt: Instant = submission.submittedAt
    val rejectReasonIfAny: String? = submission.rejectReason

    val showsSimilarCatalogueItemsSection: Boolean
        get() = submission.barcode == null

    // MARK: - Functions

    suspend fun onAppear() {
        if (!showsSimilarCatalogueItemsSection) return
        try {
            _similarCatalogueItems.value = searchFoodItems(submission.item.czName)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.MODERATION)
            _isSimilarCatalogueItemsSectionAvailable.value = false
        }
    }

    suspend fun onNutritionLabelCaptured(image: NutritionLabelImage, liveBarcode: String?) {
        recognizeNutritionLabel(image, liveBarcode, recognizeNutritionLabelUseCase)
    }

    fun onNutritionLabelCameraTapped() {
        isNutritionLabelCameraVisible.value = true
    }

    suspend fun onApproveTapped() {
        if (_state.value.isLoading) return
        _state.value = LoadingState.Loading
        val editedItem = formInput.value.asFoodItemDomain(date = submission.item.date)
        try {
            approveSubmission(submission, editedItem)
            onResolved()
            _shouldDismiss.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApproveSubmissionError.AlreadyResolved) {
            onResolved()
            alertItem.value = AlertItem(titleRes = R.string.moderation_error_alreadyResolved)
            _shouldDismiss.value = true
        } catch (_: ApproveSubmissionError.ChangedSinceReview) {
            onResolved()
            alertItem.value = AlertItem(titleRes = R.string.moderation_error_changedSinceReview)
            _shouldDismiss.value = true
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(
                titleRes = when (error) {
                    is CreateFoodItemError.InvalidCode -> R.string.addFood_error_invalidCode
                    is CreateFoodItemError.InvalidName -> R.string.addFood_error_invalidName
                    is CreateFoodItemError.InvalidCalories -> R.string.addFood_error_invalidCalories
                    is CreateFoodItemError.InvalidWeight -> R.string.addFood_error_invalidWeight
                    is CreateFoodItemError.InvalidPortion -> error.error.alertTitleRes
                    is CreateFoodItemError.ItemAlreadyExists -> R.string.moderation_error_alreadyExists
                    else -> R.string.common_error_unknown
                },
            )
        } finally {
            _state.value = LoadingState.loaded
        }
    }

    suspend fun onRejectConfirmed() {
        if (_state.value.isLoading) return
        _state.value = LoadingState.Loading
        try {
            rejectSubmission(submission, rejectReason.value)
            onResolved()
            _shouldDismiss.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: RejectSubmissionError.ReasonRequired) {
            alertItem.value = AlertItem(titleRes = R.string.moderation_error_reasonRequired)
        } catch (_: RejectSubmissionError.AlreadyResolved) {
            onResolved()
            alertItem.value = AlertItem(titleRes = R.string.moderation_error_alreadyResolved)
            _shouldDismiss.value = true
        } catch (_: RejectSubmissionError.ChangedSinceReview) {
            onResolved()
            alertItem.value = AlertItem(titleRes = R.string.moderation_error_changedSinceReview)
            _shouldDismiss.value = true
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.MODERATION)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            _state.value = LoadingState.loaded
        }
    }
}
