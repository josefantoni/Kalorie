package antoni.kalorie.features.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationQueueView(
    viewModel: ModerationQueueViewModel,
    onDismiss: () -> Unit,
    makeReviewView: @Composable (submission: FoodItemSubmissionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) -> Unit,
    makeCatalogueEditorView: @Composable (barcode: String?, onDismiss: () -> Unit) -> Unit,
) {

    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val collidingBarcodes by viewModel.collidingBarcodes.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    var reviewedSubmission by remember { mutableStateOf<FoodItemSubmissionDomain?>(null) }
    var isCatalogueEditorPushed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.moderation_queue_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isCatalogueEditorPushed = true }) {
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
                    items(submissions, key = { it.id }) { submission ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reviewedSubmission = submission }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(text = submission.item.displayName, style = MaterialTheme.typography.bodyLarge)
                            if (submission.barcode in collidingBarcodes) {
                                Text(
                                    text = stringResource(R.string.moderation_queue_collision),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (state.isLoading) {
                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    reviewedSubmission?.let { submission ->
        makeReviewView(
            submission,
            { scope.launch { viewModel.onSubmissionResolved(submission.id) } },
            { reviewedSubmission = null },
        )
    }

    if (isCatalogueEditorPushed) {
        makeCatalogueEditorView(null) { isCatalogueEditorPushed = false }
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
