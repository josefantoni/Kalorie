package antoni.kalorie.features.notifications

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCase
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCase
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCase
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCase
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCase
import antoni.kalorie.core.usecases.MaintainerClaimCache
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner
import antoni.kalorie.features.moderation.ModerationConfigurator

class NotificationsConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val maintainerClaimCache: MaintainerClaimCache,
) {

    // MARK: - Properties

    private val moderationConfigurator = ModerationConfigurator(dataProvider, authProvider)

    // MARK: - Functions

    @Composable
    fun createBellViewModel(userId: String?): NotificationBellViewModel = viewModel(key = "bell/$userId") {
        NotificationBellViewModel(
            fetchMaintainerClaim = FetchMaintainerClaimUseCase(authProvider, maintainerClaimCache, forcesRefresh = false),
            fetchHasPendingNotifications = FetchHasPendingNotificationsUseCase(dataProvider),
        )
    }

    @Composable
    fun createView(onDismiss: () -> Unit) {
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            NotificationsViewModel(
                fetchPendingSubmissions = FetchPendingSubmissionsUseCase(dataProvider, authProvider),
                fetchFoodItemReports = FetchFoodItemReportsUseCase(dataProvider, authProvider),
                fetchPhotoSuggestions = FetchPhotoSuggestionsUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
            )
        }
        NotificationsView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeSubmissionView = { submission, onResolved, onViewDismiss ->
                moderationConfigurator.MakeReviewView(submission, onResolved, onViewDismiss)
            },
            makeReportView = { barcode, reports, onReportsChanged, onViewDismiss ->
                moderationConfigurator.MakeCatalogueEditorView(initialBarcode = barcode, onDismiss = onViewDismiss, reports = reports, onReportsChanged = onReportsChanged)
            },
            makePhotoSuggestionView = { suggestion, onResolved, onViewDismiss ->
                moderationConfigurator.MakePhotoSuggestionView(suggestion, onResolved, onViewDismiss)
            },
        )
    }
}
