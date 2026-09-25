package antoni.kalorie.features.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.utils.rememberDialogViewModelStoreOwner
import antoni.kalorie.KalorieApplication
import antoni.kalorie.core.auth.AuthCommandProvider
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.GoogleSessionProvider
import antoni.kalorie.core.auth.GoogleSignInProvider
import antoni.kalorie.core.auth.MergeStatusReporting
import antoni.kalorie.core.auth.PendingMergeSnapshotStore
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.DeleteAccountUseCase
import antoni.kalorie.core.usecases.FetchMaintainerClaimUseCase
import antoni.kalorie.core.usecases.LinkOrMergeCredentialUseCase
import antoni.kalorie.core.usecases.MaintainerClaimCache
import antoni.kalorie.core.usecases.MigrateAnonymousDataUseCase
import antoni.kalorie.core.usecases.ReauthenticateUseCase
import antoni.kalorie.core.usecases.SignInWithGoogleUseCase
import antoni.kalorie.core.usecases.SignOutUseCase
import antoni.kalorie.features.moderation.ModerationConfigurator

class AccountConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val mergeStatusReporting: MergeStatusReporting,
) {

    // MARK: - Properties

    private val maintainerClaimCache = MaintainerClaimCache()
    private val moderationConfigurator = ModerationConfigurator(dataProvider, authProvider)

    // MARK: - Functions

    @Composable
    fun createView(onDismiss: () -> Unit) {
        val context = LocalContext.current.applicationContext
        val viewModel = viewModel(viewModelStoreOwner = rememberDialogViewModelStoreOwner()) {
            val authCommandProvider = AuthCommandProvider()
            val snapshotStore = PendingMergeSnapshotStore(context.filesDir)
            val googleSignInProvider = GoogleSignInProvider(context, (context as KalorieApplication).currentActivityProvider)
            val migrateAnonymousData = MigrateAnonymousDataUseCase(
                dataProvider = dataProvider,
                authProvider = authProvider,
                authCommandProvider = authCommandProvider,
                snapshotStore = snapshotStore,
            )
            val linkOrMergeCredential = LinkOrMergeCredentialUseCase(
                authProvider = authProvider,
                authCommandProvider = authCommandProvider,
                migrateAnonymousData = migrateAnonymousData,
            )
            AccountViewModel(
                authProvider = authProvider,
                signOut = SignOutUseCase(
                    authCommandProvider = authCommandProvider,
                    snapshotStore = snapshotStore,
                    googleSessionProvider = GoogleSessionProvider(context),
                ),
                signInWithGoogle = SignInWithGoogleUseCase(
                    googleSignInProvider = googleSignInProvider,
                    linkOrMergeCredential = linkOrMergeCredential,
                    authCommandProvider = authCommandProvider,
                    dataProvider = dataProvider,
                    authProvider = authProvider,
                ),
                deleteAccount = DeleteAccountUseCase(
                    dataProvider = dataProvider,
                    authProvider = authProvider,
                    authCommandProvider = authCommandProvider,
                ),
                reauthenticate = ReauthenticateUseCase(
                    googleSignInProvider = googleSignInProvider,
                    authCommandProvider = authCommandProvider,
                    authProvider = authProvider,
                ),
                fetchMaintainerClaim = FetchMaintainerClaimUseCase(maintainerClaimCache),
                mergeStatusReporting = mergeStatusReporting,
            )
        }
        AccountView(
            viewModel = viewModel,
            onDismiss = onDismiss,
            makeModerationView = { onModerationDismiss -> moderationConfigurator.createView(onDismiss = onModerationDismiss) },
            makeModerationReportsView = { onReportsDismiss -> moderationConfigurator.createReportsView(onDismiss = onReportsDismiss) },
        )
    }
}
