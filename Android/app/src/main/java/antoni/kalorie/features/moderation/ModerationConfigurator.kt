package antoni.kalorie.features.moderation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.nutritionlabelrecognition.MlKitTextRecognizer
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCase
import antoni.kalorie.core.usecases.ApproveSubmissionUseCase
import antoni.kalorie.core.usecases.CreateFoodItemUseCase
import antoni.kalorie.core.usecases.DeleteFoodItemReportUseCase
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCase
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCase
import antoni.kalorie.core.usecases.RejectSubmissionUseCase
import antoni.kalorie.core.usecases.SearchFoodItemsUseCase
import antoni.kalorie.core.usecases.UpdateFoodItemUseCase
import java.util.UUID

class ModerationConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(onDismiss: () -> Unit) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            ModerationQueueViewModel(
                fetchPendingSubmissions = FetchPendingSubmissionsUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
            )
        }
        ModerationQueueView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeReviewView = { submission, onResolved, onReviewDismiss ->
                MakeReviewView(submission = submission, onResolved = onResolved, onDismiss = onReviewDismiss)
            },
            makeCatalogueEditorView = { barcode, onEditorDismiss ->
                MakeCatalogueEditorView(initialBarcode = barcode, onDismiss = onEditorDismiss)
            },
        )
    }

    @Composable
    fun createReportsView(onDismiss: () -> Unit) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            ModerationReportsViewModel(
                fetchFoodItemReports = FetchFoodItemReportsUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                deleteFoodItemReport = DeleteFoodItemReportUseCase(dataProvider, authProvider),
            )
        }
        ModerationReportsView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeCatalogueEditorView = { barcode, onEditorDismiss ->
                MakeCatalogueEditorView(initialBarcode = barcode, onDismiss = onEditorDismiss)
            },
        )
    }

    // MARK: - Private

    @Composable
    private fun MakeReviewView(submission: FoodItemSubmissionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            ModerationReviewViewModel(
                submission = submission,
                approveSubmission = ApproveSubmissionUseCase(
                    dataProvider = dataProvider,
                    authProvider = authProvider,
                    createFoodItem = CreateFoodItemUseCase(dataProvider),
                ),
                rejectSubmission = RejectSubmissionUseCase(dataProvider, authProvider),
                searchFoodItems = SearchFoodItemsUseCase(dataProvider),
                recognizeNutritionLabelUseCase = MlKitTextRecognizer().let { RecognizeNutritionLabelUseCase(it, it) },
                onResolved = onResolved,
            )
        }
        ModerationReviewView(viewModel = viewModel, onDismiss = onDismiss)
    }

    @Composable
    private fun MakeCatalogueEditorView(initialBarcode: String?, onDismiss: () -> Unit) {
        val instanceKey = remember { UUID.randomUUID().toString() }
        val viewModel = viewModel(key = instanceKey) {
            ModerationCatalogueEditorViewModel(
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                updateFoodItem = UpdateFoodItemUseCase(dataProvider, authProvider),
                recognizeNutritionLabelUseCase = MlKitTextRecognizer().let { RecognizeNutritionLabelUseCase(it, it) },
                initialBarcode = initialBarcode,
            )
        }
        ModerationCatalogueEditorView(viewModel = viewModel, onDismiss = onDismiss)
    }
}
