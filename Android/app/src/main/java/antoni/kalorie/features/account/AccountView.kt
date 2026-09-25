package antoni.kalorie.features.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountView(viewModel: AccountViewModel, onDismiss: () -> Unit) {

    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    val isReauthenticateAlertVisible by viewModel.isReauthenticateAlertVisible.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.account_navigationTitle)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                )
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.account_dataAttribution),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { uriHandler.openUri("https://${Constants.OpenFoodFacts.HOST}") },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.account_dataAttribution_linkTitle))
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (viewModel.isAnonymous) {
                        Text(
                            text = stringResource(R.string.account_anonymous_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { scope.launch { viewModel.onSignInWithGoogleTapped() } },
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) {
                            Text(stringResource(R.string.account_button_signInWithGoogle))
                        }
                    } else {
                        Text(
                            text = viewModel.displayName ?: stringResource(R.string.account_signedIn_defaultName),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        TextButton(
                            onClick = { scope.launch { viewModel.onSignOutTapped() } },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.account_button_signOut))
                        }
                        TextButton(
                            onClick = { viewModel.showDeleteConfirmation.value = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.account_button_deleteAccount))
                        }
                    }
                }
                if (state != AccountViewModel.State.IDLE) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    alertItem?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(alert.titleRes)) },
            text = alert.messageRes?.let { messageRes -> { Text(stringResource(messageRes)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation.value = false },
            title = { Text(stringResource(R.string.account_alert_deleteConfirmTitle)) },
            text = { Text(stringResource(R.string.account_alert_deleteConfirmMessage)) },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirmation.value = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showDeleteConfirmation.value = false
                        scope.launch { viewModel.onDeleteAccountConfirmed() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.account_button_deleteAccount))
                }
            },
        )
    }

    if (isReauthenticateAlertVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isReauthenticateAlertVisible.value = false },
            title = { Text(stringResource(R.string.account_alert_reauthenticateTitle)) },
            text = { Text(stringResource(R.string.account_error_deleteRequiresRecentLogin)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isReauthenticateAlertVisible.value = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isReauthenticateAlertVisible.value = false
                        scope.launch { viewModel.onReauthenticateConfirmed() }
                    },
                ) {
                    Text(stringResource(R.string.account_button_reauthenticate))
                }
            },
        )
    }
}
