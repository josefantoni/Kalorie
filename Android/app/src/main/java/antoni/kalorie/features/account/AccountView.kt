package antoni.kalorie.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            viewModel.linkInfoText?.let { linkInfoText ->
                                Text(
                                    text = stringResource(linkInfoText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                )
                            }
                            if (!viewModel.areAllProvidersLinked) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                                ) {
                                    LinkTile(
                                        kind = AuthProviderKind.APPLE,
                                        isLinked = AuthProviderKind.APPLE in linkedProviderKinds,
                                        onClick = { scope.launch { viewModel.onLinkProviderTapped(AuthProviderKind.APPLE) } },
                                    )
                                    LinkTile(
                                        kind = AuthProviderKind.GOOGLE,
                                        isLinked = AuthProviderKind.GOOGLE in linkedProviderKinds,
                                        onClick = { scope.launch { viewModel.onLinkProviderTapped(AuthProviderKind.GOOGLE) } },
                                    )
                                }
                            }
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
private fun LinkTile(kind: AuthProviderKind, isLinked: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        if (kind == AuthProviderKind.APPLE) R.string.account_button_linkWithApple else R.string.account_button_linkWithGoogle,
    )
    Box(
        modifier = Modifier
            .padding(bottom = if (isLinked) 0.dp else 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isLinked
            }
            .clickable(enabled = !isLinked, role = Role.Button, onClick = onClick),
    ) {
        val tileShape = RoundedCornerShape(16.dp)
        if (kind == AuthProviderKind.APPLE) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.onBackground, tileShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apple_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.background, tileShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, tileShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                )
            }
        }
        if (isLinked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(18.dp)
                    .background(AppColors.success, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        } else {
            BasicText(
                text = stringResource(R.string.account_pill_link),
                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 11.sp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 10.dp)
                    .widthIn(max = 56.dp)
                    .background(AppColors.accent, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}
