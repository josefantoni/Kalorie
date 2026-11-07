package antoni.kalorie.features.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import antoni.kalorie.components.AccentPullToRefreshBox
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.popTransitionSpec
import antoni.kalorie.components.predictivePopTransitionSpec
import antoni.kalorie.components.pushTransitionSpec
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

@Composable
fun ModerationPhotoSuggestionsView(
    viewModel: ModerationPhotoSuggestionsViewModel,
    onDismiss: () -> Unit,
    makeReviewView: @Composable (suggestion: PhotoSuggestionDomain, onResolved: () -> Unit, onDismiss: () -> Unit) -> Unit,
) {
    // MARK: - Properties

    val backStack = viewModel.backStack

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = pushTransitionSpec<ModerationPhotoSuggestionsDestination>(),
        popTransitionSpec = popTransitionSpec<ModerationPhotoSuggestionsDestination>(),
        predictivePopTransitionSpec = predictivePopTransitionSpec<ModerationPhotoSuggestionsDestination>(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { destination ->
            when (destination) {
                is ModerationPhotoSuggestionsDestination.Suggestions -> NavEntry(destination) {
                    ModerationPhotoSuggestionsContent(
                        viewModel = viewModel,
                        onDismiss = onDismiss,
                        onRowTapped = { backStack.add(ModerationPhotoSuggestionsDestination.Review(it.suggestion)) },
                    )
                }
                is ModerationPhotoSuggestionsDestination.Review -> NavEntry(destination) {
                    makeReviewView(
                        destination.suggestion,
                        { viewModel.onSuggestionResolved(destination.suggestion.id) },
                        { backStack.remove(destination) },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationPhotoSuggestionsContent(
    viewModel: ModerationPhotoSuggestionsViewModel,
    onDismiss: () -> Unit,
    onRowTapped: (PhotoSuggestionRow) -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderation_photos_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        AccentPullToRefreshBox(
            isRefreshing = false,
            onRefresh = { scope.launch { viewModel.onRefresh() } },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (rows.isEmpty() && !state.isLoading) {
                    item {
                        Text(
                            text = stringResource(R.string.moderation_photos_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item {
                    SectionCard {
                        rows.forEachIndexed { index, row ->
                            key(row.id) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRowTapped(row) }
                                        .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = row.itemName ?: row.suggestion.itemId,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (index < rows.lastIndex) SectionCardDivider()
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
