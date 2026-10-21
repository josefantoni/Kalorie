package antoni.kalorie.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
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
    val showSignOutConfirmation by viewModel.showSignOutConfirmation.collectAsState()
    val isLinkInfoPopoverVisible by viewModel.isLinkInfoPopoverVisible.collectAsState()
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
                if (!isAnonymous) {
                    PrimaryButton(
                        text = stringResource(R.string.account_button_deleteAccount),
                        onClick = { viewModel.showDeleteConfirmation.value = true },
                        style = PrimaryButtonStyle.DESTRUCTIVE,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    if (isAnonymous) {
                        SectionCard {
                            Text(
                                text = stringResource(R.string.account_anonymous_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                            ) {
                                SignInTile(
                                    kind = AuthProviderKind.APPLE,
                                    onClick = { scope.launch { viewModel.onSignInWithAppleTapped() } },
                                )
                                SignInTile(
                                    kind = AuthProviderKind.GOOGLE,
                                    onClick = { scope.launch { viewModel.onSignInWithGoogleTapped() } },
                                )
                            }
                        }
                    } else {
                        SectionCard {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = SectionRowMinHeight).padding(start = 16.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = displayName ?: stringResource(R.string.account_signedIn_defaultName),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Box {
                                    IconButton(onClick = { viewModel.isLinkInfoPopoverVisible.value = true }) {
                                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    DropdownMenu(
                                        expanded = isLinkInfoPopoverVisible,
                                        onDismissRequest = { viewModel.isLinkInfoPopoverVisible.value = false },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.widthIn(max = 280.dp).padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            viewModel.linkInfoText?.let { linkInfoText ->
                                                Text(
                                                    text = stringResource(linkInfoText),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
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
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionCard {
                        LinkRow(
                            title = stringResource(R.string.common_privacyPolicy),
                            onClick = { uriHandler.openUri(Constants.Support.PRIVACY_POLICY_URL) },
                        )
                        SectionCardDivider()
                        LinkRow(
                            title = stringResource(R.string.account_dataAttribution),
                            isDescription = true,
                            onClick = { uriHandler.openUri("https://${Constants.OpenFoodFacts.HOST}") },
                        )
                        SectionCardDivider()
                        LinkRow(
                            title = stringResource(R.string.account_ciqualAttribution),
                            isDescription = true,
                            onClick = { uriHandler.openUri(Constants.Ciqual.DATASET_URL) },
                        )
                    }
                    if (!isAnonymous) {
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { viewModel.showSignOutConfirmation.value = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.account_button_signOut),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
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

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.showSignOutConfirmation.value = false },
            title = { Text(stringResource(R.string.account_alert_signOutConfirmTitle)) },
            dismissButton = {
                TextButton(onClick = { viewModel.showSignOutConfirmation.value = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showSignOutConfirmation.value = false
                        scope.launch { viewModel.onSignOutTapped() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.error),
                ) {
                    Text(stringResource(R.string.account_button_signOut))
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
private fun LinkRow(title: String, onClick: () -> Unit, isDescription: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = SectionRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = if (isDescription) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderLogo(kind: AuthProviderKind) {
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
}

@Composable
private fun SignInTile(kind: AuthProviderKind, onClick: () -> Unit) {
    val description = stringResource(
        if (kind == AuthProviderKind.APPLE) R.string.account_button_signInWithApple else R.string.account_button_signInWithGoogle,
    )
    Column(
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProviderLogo(kind)
        Text(
            text = stringResource(
                if (kind == AuthProviderKind.APPLE) R.string.account_signInProvider_apple else R.string.account_signInProvider_google,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        ProviderLogo(kind)
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
