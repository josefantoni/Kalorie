package antoni.kalorie.features.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProvider
import antoni.kalorie.core.usecases.FetchFoodItemByBarcodeUseCase
import antoni.kalorie.core.usecases.FetchFoodItemReportsUseCase
import antoni.kalorie.core.usecases.FetchHasPendingNotificationsUseCase
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCase
import antoni.kalorie.core.usecases.FetchPendingSubmissionsUseCase
import antoni.kalorie.core.usecases.FetchPhotoSuggestionsUseCase
import antoni.kalorie.core.usecases.FetchUserNotificationsUseCase
import antoni.kalorie.core.usecases.HideUserNotificationUseCase
import antoni.kalorie.core.usecases.MaintainerClaimCache
import antoni.kalorie.core.usecases.MarkUserNotificationSeenUseCase
import antoni.kalorie.core.usecases.WithdrawUserNotificationUseCase
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
    fun createBellViewModel(userId: String?): NotificationBellViewModel {
        val context = LocalContext.current.applicationContext
        val notificationsStore = remember(userId) { UserNotificationsStore(context, authProvider) }
        return viewModel(key = "bell/$userId") {
            NotificationBellViewModel(
                fetchMaintainerClaim = FetchMaintainerClaimUseCase(authProvider, maintainerClaimCache, forcesRefresh = false),
                fetchHasPendingNotifications = FetchHasPendingNotificationsUseCase(dataProvider),
                fetchUserNotifications = FetchUserNotificationsUseCase(dataProvider, authProvider),
                notificationsStore = notificationsStore,
            )
        }
    }

    @Composable
    fun createView(isMaintainer: Boolean, onDismiss: () -> Unit) {
        val context = LocalContext.current.applicationContext
        val notificationsStore = remember { UserNotificationsStore(context, authProvider) }
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            NotificationsViewModel(
                fetchPendingSubmissions = FetchPendingSubmissionsUseCase(dataProvider, authProvider),
                fetchFoodItemReports = FetchFoodItemReportsUseCase(dataProvider, authProvider),
                fetchPhotoSuggestions = FetchPhotoSuggestionsUseCase(dataProvider, authProvider),
                fetchFoodItemByBarcode = FetchFoodItemByBarcodeUseCase(dataProvider),
                isMaintainer = isMaintainer,
                fetchUserNotifications = FetchUserNotificationsUseCase(dataProvider, authProvider),
                markUserNotificationSeen = MarkUserNotificationSeenUseCase(dataProvider, authProvider),
                hideUserNotification = HideUserNotificationUseCase(dataProvider, authProvider),
                withdrawUserNotification = WithdrawUserNotificationUseCase(dataProvider, authProvider, StorageDataProvider()),
                notificationsStore = notificationsStore,
            )
        }
        NotificationsView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeSubmissionView = { submission, onResolved, onViewDismiss ->
                moderationConfigurator.MakeReviewView(submission, onResolved, onViewDismiss)
            },
            makeReportView = { report, onResolved, onViewDismiss ->
                moderationConfigurator.MakeCatalogueEditorView(onDismiss = onViewDismiss, ticket = report, onResolved = onResolved)
            },
            makePhotoSuggestionView = { suggestion, onResolved, onViewDismiss ->
                moderationConfigurator.MakePhotoSuggestionView(suggestion, onResolved, onViewDismiss)
            },
        )
    }
}
