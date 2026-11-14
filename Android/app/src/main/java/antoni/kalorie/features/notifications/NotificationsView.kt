package antoni.kalorie.features.notifications

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.R
import antoni.kalorie.components.AccentPullToRefreshBox
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.components.RevealingSwipeToDeleteRow
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.bottomOnly
import antoni.kalorie.components.excludingBottom
import antoni.kalorie.components.popTransitionSpec
import antoni.kalorie.components.predictivePopTransitionSpec
import antoni.kalorie.components.pushTransitionSpec
import antoni.kalorie.components.sheetContentWindowInsets
import antoni.kalorie.components.sheetScrollGuard
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsView(
    viewModel: NotificationsViewModel,
    onDismiss: () -> Unit,
    makeSubmissionView: @Composable (submission: FoodItemSubmissionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) -> Unit,
    makeReportView: @Composable (report: FoodItemReportDomain, onResolved: () -> Unit, onDismiss: () -> Unit) -> Unit,
    makePhotoSuggestionView: @Composable (suggestion: PhotoSuggestionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) -> Unit,
) {
    // MARK: - Properties

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { sheetContentWindowInsets() },
        sheetGesturesEnabled = viewModel.backStack.size <= 1,
    ) {
        NavDisplay(
            modifier = Modifier.sheetScrollGuard(),
            backStack = viewModel.backStack,
            onBack = { viewModel.backStack.removeLastOrNull() },
            transitionSpec = pushTransitionSpec<NotificationsDestination>(),
            popTransitionSpec = popTransitionSpec<NotificationsDestination>(),
            predictivePopTransitionSpec = predictivePopTransitionSpec<NotificationsDestination>(),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { destination ->
                when (destination) {
                    is NotificationsDestination.List -> NavEntry(destination) {
                        NotificationsContent(
                            viewModel = viewModel,
                            onDismiss = onDismiss,
                            onRowTapped = { row -> viewModel.backStack.add(row.destination()) },
                        )
                    }
                    is NotificationsDestination.Submission -> NavEntry(destination) {
                        makeSubmissionView(
                            destination.submission,
                            { viewModel.onRowResolved(destination.rowId) },
                            { viewModel.backStack.remove(destination) },
                        )
                    }
                    is NotificationsDestination.Report -> NavEntry(destination) {
                        makeReportView(
                            destination.report,
                            { viewModel.onRowResolved(destination.rowId) },
                            { viewModel.backStack.remove(destination) },
                        )
                    }
                    is NotificationsDestination.PhotoSuggestion -> NavEntry(destination) {
                        makePhotoSuggestionView(
                            destination.suggestion,
                            { viewModel.onRowResolved(destination.rowId) },
                            { viewModel.backStack.remove(destination) },
                        )
                    }
                    is NotificationsDestination.UserNotification -> NavEntry(destination) {
                        UserNotificationDetailView(
                            title = destination.title,
                            notification = destination.notification,
                            onShown = { viewModel.onDetailShown(destination.rowId) },
                            onWithdraw = {
                                val result = viewModel.onWithdrawConfirmed(destination.rowId)
                                if (result == WithdrawResult.ALREADY_RESOLVED) viewModel.backStack.remove(destination)
                                result
                            },
                            onBack = { viewModel.backStack.remove(destination) },
                        )
                    }
                }
            },
        )
    }
}

private fun NotificationRow.destination(): NotificationsDestination = when (val kind = kind) {
    is NotificationRow.Kind.Submission -> NotificationsDestination.Submission(id, kind.submission)
    is NotificationRow.Kind.Report -> NotificationsDestination.Report(id, kind.report)
    is NotificationRow.Kind.PhotoSuggestion -> NotificationsDestination.PhotoSuggestion(id, kind.suggestion)
    is NotificationRow.Kind.UserNotification -> NotificationsDestination.UserNotification(id, title, kind.notification)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsContent(
    viewModel: NotificationsViewModel,
    onDismiss: () -> Unit,
    onRowTapped: (NotificationRow) -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()
    var openSwipeRowId by remember { mutableStateOf<String?>(null) }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_button_cancel))
                    }
                },
            )
        },
    ) { innerPadding ->
        AccentPullToRefreshBox(
            isRefreshing = false,
            onRefresh = { scope.launch { viewModel.onRefresh() } },
            modifier = Modifier.fillMaxSize().padding(innerPadding.excludingBottom()),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding.bottomOnly()) {
                if (rows.isEmpty()) {
                    if (!state.isLoading) {
                        item {
                            Column(
                                modifier = Modifier.fillParentMaxSize().offset(y = (-100).dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(72.dp),
                                )
                                Text(
                                    text = stringResource(R.string.notifications_empty),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        SectionCard {
                            rows.forEachIndexed { index, row ->
                                key(row.id) {
                                    val onClick = { if (openSwipeRowId == null) onRowTapped(row) else openSwipeRowId = null }
                                    if (row.isHideable) {
                                        RevealingSwipeToDeleteRow(
                                            id = row.id,
                                            openRowId = openSwipeRowId,
                                            onOpenRowChange = { openSwipeRowId = it },
                                            onDeleteRequested = { scope.launch { viewModel.onHideTapped(row.id) } },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        ) {
                                            NotificationRowView(row = row, onClick = onClick)
                                        }
                                    } else {
                                        NotificationRowView(row = row, onClick = onClick)
                                    }
                                    if (index < rows.lastIndex) SectionCardDivider()
                                }
                            }
                        }
                    }
                }
            }
            if (state.isLoading) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    LoaderIndicator()
                }
            }
        }
    }

    alertItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(item.titleRes)) },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun NotificationRowView(row: NotificationRow, onClick: () -> Unit) {
    val icon = when (row.kind) {
        is NotificationRow.Kind.Submission -> Icons.Filled.AddCircleOutline
        is NotificationRow.Kind.Report -> Icons.Filled.Edit
        is NotificationRow.Kind.PhotoSuggestion -> Icons.Filled.PhotoCamera
        is NotificationRow.Kind.UserNotification -> row.kind.notification.kind.icon()
    }
    val kindLabel = when (row.kind) {
        is NotificationRow.Kind.Submission -> R.string.notifications_kind_submission
        is NotificationRow.Kind.Report -> R.string.notifications_kind_report
        is NotificationRow.Kind.PhotoSuggestion -> R.string.notifications_kind_photo
        is NotificationRow.Kind.UserNotification -> row.kind.notification.statusLabelRes()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val unreadDescription = stringResource(R.string.notifications_user_unread_accessibility)
        BadgedBox(
            badge = {
                if (row.isUnread) {
                    Badge(
                        containerColor = AppColors.error,
                        modifier = Modifier.semantics { contentDescription = unreadDescription },
                    )
                }
            },
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(kindLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
