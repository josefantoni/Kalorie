package antoni.kalorie.features.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.FoodPhotoRemoteContent
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionGroup
import antoni.kalorie.components.SectionStack
import antoni.kalorie.components.sheetBottomBar
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserNotificationDetailView(
    title: String,
    notification: UserNotificationDomain,
    onShown: suspend () -> Unit,
    onWithdraw: suspend () -> WithdrawResult,
    onBack: () -> Unit,
) {
    // MARK: - Properties

    var isWithdrawConfirmationVisible by remember { mutableStateOf(false) }
    var isWithdrawErrorVisible by remember { mutableStateOf(false) }
    var isWithdrawing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onShown() }
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    val sentAt = formatter.format(notification.createdAt)

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            if (notification.status == TicketStatus.PENDING) {
                PrimaryButton(
                    text = stringResource(R.string.notifications_user_withdraw_button),
                    onClick = { isWithdrawConfirmationVisible = true },
                    style = PrimaryButtonStyle.DESTRUCTIVE,
                    enabled = !isWithdrawing,
                    modifier = Modifier.sheetBottomBar().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) { innerPadding ->
        SectionStack(modifier = Modifier.padding(innerPadding)) {
            SectionCard {
                notification.photoUrl?.let { photoUrl ->
                    FoodPhotoRemoteContent(
                        url = photoUrl,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    SectionCardDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.notifications_user_status_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(notification.statusLabelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when (notification.status) {
                            TicketStatus.PENDING -> MaterialTheme.colorScheme.onSurface
                            TicketStatus.ACCEPTED -> AppColors.success
                            TicketStatus.REJECTED -> AppColors.error
                        },
                    )
                }
                if (notification.status == TicketStatus.PENDING) {
                    SectionCardDivider()
                    Text(
                        text = stringResource(notification.kind.messageRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                SectionCardDivider()
                Text(
                    text = stringResource(R.string.notifications_user_sent_at, sentAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            notification.reason?.let { reason ->
                SectionGroup(title = stringResource(R.string.notifications_user_reason_title)) {
                    SectionCard {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            val response = notification.response
            if (notification.status != TicketStatus.PENDING && response != null) {
                SectionGroup(title = stringResource(R.string.notifications_user_response_title)) {
                    SectionCard {
                        Text(
                            text = response,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        notification.resolvedAt?.let { resolvedAt ->
                            SectionCardDivider()
                            Text(
                                text = stringResource(R.string.notifications_user_resolved_at, formatter.format(resolvedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (isWithdrawConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { isWithdrawConfirmationVisible = false },
            title = { Text(stringResource(R.string.notifications_user_withdraw_confirm_title)) },
            dismissButton = {
                TextButton(onClick = { isWithdrawConfirmationVisible = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isWithdrawConfirmationVisible = false
                        scope.launch {
                            isWithdrawing = true
                            try {
                                when (onWithdraw()) {
                                    WithdrawResult.WITHDRAWN, WithdrawResult.ALREADY_RESOLVED -> onBack()
                                    WithdrawResult.FAILED -> isWithdrawErrorVisible = true
                                }
                            } finally {
                                isWithdrawing = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.notifications_user_withdraw_button))
                }
            },
        )
    }

    if (isWithdrawErrorVisible) {
        AlertDialog(
            onDismissRequest = { isWithdrawErrorVisible = false },
            title = { Text(stringResource(R.string.common_error_unknown)) },
            confirmButton = {
                TextButton(onClick = { isWithdrawErrorVisible = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

fun UserNotificationDomain.Kind.icon() = when (this) {
    UserNotificationDomain.Kind.REPORT -> Icons.Filled.Edit
    UserNotificationDomain.Kind.PHOTO_SUGGESTION -> Icons.Filled.PhotoCamera
}

fun UserNotificationDomain.statusLabelRes() = when (status) {
    TicketStatus.PENDING -> R.string.notifications_user_status_pending
    TicketStatus.ACCEPTED -> when (kind) {
        UserNotificationDomain.Kind.REPORT -> R.string.notifications_user_status_resolved
        UserNotificationDomain.Kind.PHOTO_SUGGESTION -> R.string.notifications_user_status_approved
    }
    TicketStatus.REJECTED -> R.string.notifications_user_status_rejected
}

private fun UserNotificationDomain.Kind.messageRes() = when (this) {
    UserNotificationDomain.Kind.REPORT -> R.string.notifications_user_report_message
    UserNotificationDomain.Kind.PHOTO_SUGGESTION -> R.string.notifications_user_photo_message
}
