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
import androidx.compose.material.icons.filled.Edit
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
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

@Composable
fun ModerationQueueView(
    viewModel: ModerationQueueViewModel,
    onDismiss: () -> Unit,
    makeReviewView: @Composable (submission: FoodItemSubmissionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) -> Unit,
    makeCatalogueEditorView: @Composable (barcode: String?, onDismiss: () -> Unit) -> Unit,
) {
    // MARK: - Properties

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    NavDisplay(
        backStack = viewModel.backStack,
        onBack = { viewModel.backStack.removeLastOrNull() },
        transitionSpec = pushTransitionSpec<ModerationQueueDestination>(),
        popTransitionSpec = popTransitionSpec<ModerationQueueDestination>(),
        predictivePopTransitionSpec = predictivePopTransitionSpec<ModerationQueueDestination>(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { destination ->
            when (destination) {
                is ModerationQueueDestination.Queue -> NavEntry(destination) {
                    ModerationQueueContent(
                        viewModel = viewModel,
                        onDismiss = onDismiss,
                        onSubmissionTapped = { viewModel.backStack.add(ModerationQueueDestination.Review(it)) },
                        onCatalogueEditorTapped = { viewModel.backStack.add(ModerationQueueDestination.CatalogueEditor) },
                    )
                }
                is ModerationQueueDestination.Review -> NavEntry(destination) {
                    val submission = destination.submission
                    makeReviewView(
                        submission,
                        { scope.launch { viewModel.onSubmissionResolved(submission.id) } },
                        { viewModel.backStack.remove(destination) },
                    )
                }
                is ModerationQueueDestination.CatalogueEditor -> NavEntry(destination) {
                    makeCatalogueEditorView(null) { viewModel.backStack.remove(destination) }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationQueueContent(
    viewModel: ModerationQueueViewModel,
    onDismiss: () -> Unit,
    onSubmissionTapped: (FoodItemSubmissionDomain) -> Unit,
    onCatalogueEditorTapped: () -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val collidingBarcodes by viewModel.collidingBarcodes.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderation_queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onCatalogueEditorTapped) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
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
                if (submissions.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            text = stringResource(R.string.moderation_queue_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item {
                    SectionCard {
                        submissions.forEachIndexed { index, submission ->
                            key(submission.id) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSubmissionTapped(submission) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = submission.item.displayName, style = MaterialTheme.typography.bodyLarge)
                                        if (submission.barcode in collidingBarcodes) {
                                            Text(
                                                text = stringResource(R.string.moderation_queue_collision),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (index < submissions.lastIndex) SectionCardDivider()
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
