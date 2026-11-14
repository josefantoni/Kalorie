package antoni.kalorie.features.moderation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.KalorieApplication
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProvider
import antoni.kalorie.core.usecases.ApprovePhotoSuggestionUseCase
import antoni.kalorie.core.usecases.ApproveSubmissionUseCase
import antoni.kalorie.core.usecases.CreateFoodItemUseCase
import antoni.kalorie.core.usecases.DeleteFoodPhotoUseCase
import antoni.kalorie.core.usecases.DownloadFoodPhotoUseCase
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCase
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCase
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCase
import antoni.kalorie.core.usecases.RejectPhotoSuggestionUseCase
import antoni.kalorie.core.usecases.RejectSubmissionUseCase
import antoni.kalorie.core.usecases.ResolveFoodItemReportUseCase
import antoni.kalorie.core.usecases.SearchFoodItemsUseCase
import antoni.kalorie.core.usecases.UpdateFoodItemUseCase
import antoni.kalorie.core.usecases.UploadFoodPhotoUseCase
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner

class ModerationConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Properties

    private val storageProvider = StorageDataProvider()

    // MARK: - Functions

    @Composable
    fun createView(onDismiss: () -> Unit) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
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
            makeCatalogueEditorView = { onEditorDismiss ->
                MakeCatalogueEditorView(onDismiss = onEditorDismiss)
            },
        )
    }

    @Composable
    fun createReportsView(onDismiss: () -> Unit) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            ModerationReportsViewModel(
                fetchFoodItemReports = FetchFoodItemReportsUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
            )
        }
        ModerationReportsView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeCatalogueEditorView = { report, onResolved, onEditorDismiss ->
                MakeCatalogueEditorView(onDismiss = onEditorDismiss, ticket = report, onResolved = onResolved)
            },
        )
    }

    @Composable
    fun createPhotoSuggestionsView(onDismiss: () -> Unit) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            ModerationPhotoSuggestionsViewModel(
                fetchPhotoSuggestions = FetchPhotoSuggestionsUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
            )
        }
        ModerationPhotoSuggestionsView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeReviewView = { suggestion, onResolved, onReviewDismiss ->
                MakePhotoSuggestionView(suggestion = suggestion, onResolved = onResolved, onDismiss = onReviewDismiss)
            },
        )
    }

    @Composable
    fun MakePhotoSuggestionView(suggestion: PhotoSuggestionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) {
        val context = LocalContext.current.applicationContext
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            ModerationPhotoSuggestionViewModel(
                suggestion = suggestion,
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                approvePhotoSuggestion = ApprovePhotoSuggestionUseCase(
                    dataProvider = dataProvider,
                    authProvider = authProvider,
                    storageProvider = storageProvider,
                    fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                    downloadFoodPhoto = DownloadFoodPhotoUseCase(storageProvider),
                    updateFoodItem = UpdateFoodItemUseCase(
                        dataProvider,
                        authProvider,
                        UploadFoodPhotoUseCase(storageProvider),
                        DeleteFoodPhotoUseCase(storageProvider),
                    ),
                ),
                rejectPhotoSuggestion = RejectPhotoSuggestionUseCase(dataProvider, authProvider, storageProvider),
                responseTextProvider = ModerationResponseTextProvider(context),
                onResolved = onResolved,
            )
        }
        ModerationPhotoSuggestionView(viewModel = viewModel, onDismiss = onDismiss)
    }

    @Composable
    fun MakeReviewView(submission: FoodItemSubmissionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            ModerationReviewViewModel(
                submission = submission,
                approveSubmission = ApproveSubmissionUseCase(
                    dataProvider = dataProvider,
                    authProvider = authProvider,
                    createFoodItem = CreateFoodItemUseCase(dataProvider),
                    downloadFoodPhoto = DownloadFoodPhotoUseCase(storageProvider),
                    uploadFoodPhoto = UploadFoodPhotoUseCase(storageProvider),
                    deleteFoodPhoto = DeleteFoodPhotoUseCase(storageProvider),
                ),
                rejectSubmission = RejectSubmissionUseCase(dataProvider, authProvider),
                searchFoodItems = SearchFoodItemsUseCase(dataProvider),
                onResolved = onResolved,
            )
        }
        ModerationReviewView(viewModel = viewModel, onDismiss = onDismiss)
    }

    @Composable
    fun MakeCatalogueEditorView(
        onDismiss: () -> Unit,
        ticket: FoodItemReportDomain? = null,
        onResolved: () -> Unit = {},
    ) {
        val applicationScope = (LocalContext.current.applicationContext as KalorieApplication).applicationScope
        val context = LocalContext.current.applicationContext
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            ModerationCatalogueEditorViewModel(
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                updateFoodItem = UpdateFoodItemUseCase(dataProvider, authProvider, UploadFoodPhotoUseCase(storageProvider), DeleteFoodPhotoUseCase(storageProvider)),
                resolveFoodItemReport = ResolveFoodItemReportUseCase(dataProvider, authProvider),
                responseTextProvider = ModerationResponseTextProvider(context),
                ticket = ticket,
                applicationScope = applicationScope,
                onResolved = onResolved,
            )
        }
        ModerationCatalogueEditorView(viewModel = viewModel, onDismiss = onDismiss)
    }
}
