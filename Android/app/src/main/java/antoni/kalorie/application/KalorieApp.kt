package antoni.kalorie.application

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.BuildConfig
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.core.auth.AuthCommandProvider
import antoni.kalorie.core.auth.AuthProvider
import antoni.kalorie.core.auth.AuthStateObserver
import antoni.kalorie.core.auth.PendingMergeSnapshotStore
import antoni.kalorie.core.models.AppVersion
import antoni.kalorie.core.networking.FirestoreDataProvider
import antoni.kalorie.core.usecases.FetchMinimumVersionUseCase
import antoni.kalorie.core.usecases.MigrateAnonymousDataUseCase
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.features.dashboard.DashboardConfigurator
import antoni.kalorie.features.updaterequired.UpdateRequiredView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun KalorieApp() {
    // MARK: - Properties

    val context = LocalContext.current.applicationContext
    val authState = viewModel {
        AuthStateObserver(
            auth = FirebaseAuth.getInstance(),
            resumePendingMerge = MigrateAnonymousDataUseCase(
                dataProvider = FirestoreDataProvider(),
                authProvider = AuthProvider(),
                authCommandProvider = AuthCommandProvider(),
                snapshotStore = PendingMergeSnapshotStore(context.filesDir),
            ),
        )
    }
    val versionGate = viewModel {
        AppVersionGate(
            fetchMinimumVersion = FetchMinimumVersionUseCase(FirestoreDataProvider()),
            currentVersion = AppVersion.parse(BuildConfig.VERSION_NAME),
        )
    }
    val isUpdateRequired by versionGate.isUpdateRequired.collectAsState()
    val scope = rememberCoroutineScope()
    val state by authState.state.collectAsState()
    val userId by authState.userId.collectAsState()
    val isMerging by authState.isMerging.collectAsState()

    // MARK: - Body

    val primary = AppColors.accent
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = primary, onPrimary = Color.White) else lightColorScheme(primary = primary, onPrimary = Color.White)

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch { versionGate.check() }
    }

    MaterialTheme(colorScheme = colorScheme) {
        if (isUpdateRequired) {
            UpdateRequiredView()
            return@MaterialTheme
        }
        when (val current = state) {
            is LoadingState.Idle, is LoadingState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoaderIndicator()
            }
            is LoadingState.Loaded -> Box(modifier = Modifier.fillMaxSize()) {
                key(userId) {
                    DashboardConfigurator().createView(userId = userId, mergeStatusReporting = authState)
                }
                if (isMerging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LoaderIndicator()
                            Text(
                                text = stringResource(R.string.auth_mergingMessage),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            is LoadingState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(text = stringResource(R.string.auth_error_signInFailed), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = current.error?.localizedMessage ?: stringResource(R.string.common_error_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = authState::retry) {
                    Text(stringResource(R.string.auth_button_retry))
                }
            }
        }
    }
}
