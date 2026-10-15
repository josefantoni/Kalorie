package antoni.kalorie.features.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.R
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.popTransitionSpec
import antoni.kalorie.components.predictivePopTransitionSpec
import antoni.kalorie.components.pushTransitionSpec
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

@Composable
fun ModerationReportsView(
    viewModel: ModerationReportsViewModel,
    onDismiss: () -> Unit,
    makeCatalogueEditorView: @Composable (barcode: String, reports: List<FoodItemReportDomain>, onDismiss: () -> Unit) -> Unit,
) {
    // MARK: - Properties

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    NavDisplay(
        backStack = viewModel.backStack,
        onBack = { viewModel.backStack.removeLastOrNull() },
        transitionSpec = pushTransitionSpec<ModerationReportsDestination>(),
        popTransitionSpec = popTransitionSpec<ModerationReportsDestination>(),
        predictivePopTransitionSpec = predictivePopTransitionSpec<ModerationReportsDestination>(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { destination ->
            when (destination) {
                is ModerationReportsDestination.Reports -> NavEntry(destination) {
                    ModerationReportsContent(
                        viewModel = viewModel,
                        onDismiss = onDismiss,
                        onGroupTapped = { group ->
                            viewModel.backStack.add(ModerationReportsDestination.CatalogueEditor(group.barcode, group.reports))
                        },
                    )
                }
                is ModerationReportsDestination.CatalogueEditor -> NavEntry(destination) {
                    makeCatalogueEditorView(destination.barcode, destination.reports) {
                        viewModel.backStack.removeLastOrNull()
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationReportsContent(
    viewModel: ModerationReportsViewModel,
    onDismiss: () -> Unit,
    onGroupTapped: (ReportGroup) -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderation_reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { scope.launch { viewModel.onRefresh() } },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (groups.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            text = stringResource(R.string.moderation_reports_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item {
                    SectionCard {
                        groups.forEachIndexed { index, group ->
                            key(group.barcode) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onGroupTapped(group) }
                                        .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = group.itemName ?: group.barcode, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = stringResource(R.string.moderation_reports_count, group.reports.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { scope.launch { viewModel.onResolveTapped(group) } }) {
                                        Text(stringResource(R.string.moderation_button_resolve))
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (index < groups.lastIndex) SectionCardDivider()
                            }
                        }
                    }
                }
            }
            if (state.isLoading) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    alertItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(item.titleRes)) },
            text = item.messageRes?.let { messageRes -> { Text(stringResource(messageRes)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}
