package antoni.kalorie.features.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.sheetScrollGuard
import antoni.kalorie.core.auth.AuthProviderKind
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
    val isAnonymous by viewModel.isAnonymous.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val linkedProviderKinds by viewModel.linkedProviderKinds.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    // MARK: - Body

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Scaffold(
            modifier = Modifier.sheetScrollGuard(),
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
                    HorizontalDivider()
                    TextButton(
                        onClick = { uriHandler.openUri(Constants.Support.PRIVACY_POLICY_URL) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.common_privacyPolicy))
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                    if (isAnonymous) {
                        Text(
                            text = stringResource(R.string.account_anonymous_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Button(
                            onClick = { scope.launch { viewModel.onSignInWithAppleTapped() } },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp),
                        ) {
                            Text(stringResource(R.string.account_button_signInWithApple))
                        }
                        Button(
                            onClick = { scope.launch { viewModel.onSignInWithGoogleTapped() } },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp),
                        ) {
                            Text(stringResource(R.string.account_button_signInWithGoogle))
                        }
                    } else {
                        SectionCard {
                            Text(
                                text = displayName ?: stringResource(R.string.account_signedIn_defaultName),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                            SectionCardDivider()
                            Text(
                                text = stringResource(R.string.account_button_signOut),
                                color = AppColors.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { viewModel.onSignOutTapped() } }
                                    .padding(16.dp),
                            )
                            SectionCardDivider()
                            Text(
                                text = stringResource(R.string.account_button_deleteAccount),
                                color = AppColors.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.showDeleteConfirmation.value = true }
                                    .padding(16.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.account_signInMethods_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                        )
                        SectionCard {
                            SignInMethodRow(
                                title = stringResource(R.string.account_signInProvider_apple),
                                isLinked = AuthProviderKind.APPLE in linkedProviderKinds,
                                onLink = { scope.launch { viewModel.onLinkProviderTapped(AuthProviderKind.APPLE) } },
                            )
                            SectionCardDivider()
                            SignInMethodRow(
                                title = stringResource(R.string.account_signInProvider_google),
                                isLinked = AuthProviderKind.GOOGLE in linkedProviderKinds,
                                onLink = { scope.launch { viewModel.onLinkProviderTapped(AuthProviderKind.GOOGLE) } },
                            )
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
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.error),
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

@Composable
private fun SignInMethodRow(title: String, isLinked: Boolean, onLink: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        if (isLinked) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onLink) {
                Text(stringResource(R.string.account_button_linkProvider))
            }
        }
    }
}
