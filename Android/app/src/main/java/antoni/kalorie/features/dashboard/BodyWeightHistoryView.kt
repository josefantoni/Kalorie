package antoni.kalorie.features.dashboard

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.RevealingSwipeToDeleteRow
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.components.appSegmentedButtonColors
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.utils.LoadingState
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val SWIPE_PAGING_THRESHOLD = 24f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightHistoryView(viewModel: BodyWeightHistoryViewModel, onBack: () -> Unit) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val weights by viewModel.weights.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val isDeleteConfirmationVisible by viewModel.isDeleteConfirmationVisible.collectAsState()
    val scope = rememberCoroutineScope()
    var openSwipeRowId by remember { mutableStateOf<String?>(null) }
    val currentViewModel by rememberUpdatedState(viewModel)
    val activity = LocalActivity.current

    LaunchedEffect(Unit) { viewModel.onAppear() }
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) currentViewModel.onDisappear()
        }
    }

    // MARK: - Body

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.weightHistory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
                state is LoadingState.Error -> LoadFailedState(onRetry = { scope.launch { viewModel.onRetryTapped() } })
                weights.isEmpty() -> EmptyState()
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    item { ChartSection(viewModel) }
                    item {
                        SectionCard(modifier = Modifier.padding(top = 12.dp)) {
                            weights.forEachIndexed { index, weight ->
                                WeightRow(
                                    weight = weight,
                                    viewModel = viewModel,
                                    openSwipeRowId = openSwipeRowId,
                                    onOpenRowChange = { openSwipeRowId = it },
                                )
                                if (index < weights.lastIndex) SectionCardDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isDeleteConfirmationVisible.value = false },
            title = { Text(stringResource(R.string.weightHistory_confirm_delete)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isDeleteConfirmationVisible.value = false }) {
                    Text(stringResource(R.string.common_button_no))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isDeleteConfirmationVisible.value = false
                        scope.launch { viewModel.onDeleteConfirmed() }
                    },
                ) {
                    Text(stringResource(R.string.common_button_yes), color = AppColors.error)
                }
            },
        )
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

// MARK: - Functions

@Composable
private fun ChartSection(viewModel: BodyWeightHistoryViewModel) {
    val range by viewModel.range.collectAsState()
    val period by viewModel.period.collectAsState()
    val chartPoints by viewModel.chartPoints.collectAsState()
    val periodOffset by viewModel.periodOffset.collectAsState()
    val isLoadingEarlierYear by viewModel.isLoadingEarlierYear.collectAsState()
    val scope = rememberCoroutineScope()
    val canPageBack = remember(period) { viewModel.canPageBack }
    val canPageForward = periodOffset < 0

    SectionCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BodyWeightRange.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = range == entry,
                        onClick = {
                            viewModel.onRangeChanged(entry)
                            scope.launch { viewModel.loadEarlierYearsIfNeeded() }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BodyWeightRange.entries.size),
                        colors = appSegmentedButtonColors(),
                        icon = {},
                    ) {
                        Text(
                            stringResource(
                                when (entry) {
                                    BodyWeightRange.WEEK -> R.string.weightHistory_range_week
                                    BodyWeightRange.MONTH -> R.string.weightHistory_range_month
                                    BodyWeightRange.YEAR -> R.string.weightHistory_range_year
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { scope.launch { viewModel.onPreviousPeriod() } }, enabled = canPageBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.weightHistory_previousPeriod))
                }
                IconButton(onClick = viewModel::onNextPeriod, enabled = canPageForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.weightHistory_nextPeriod))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (totalDrag > SWIPE_PAGING_THRESHOLD) {
                                    scope.launch { viewModel.onPreviousPeriod() }
                                } else if (totalDrag < -SWIPE_PAGING_THRESHOLD) {
                                    viewModel.onNextPeriod()
                                }
                            },
                        ) { _, dragAmount -> totalDrag += dragAmount }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BodyWeightChartView(
                    points = chartPoints,
                    period = period,
                    unit = viewModel.unit,
                    modifier = Modifier.alpha(if (isLoadingEarlierYear) 0.3f else 1f),
                )
                if (isLoadingEarlierYear) CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun WeightRow(
    weight: BodyWeightDomain,
    viewModel: BodyWeightHistoryViewModel,
    openSwipeRowId: String?,
    onOpenRowChange: (String?) -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val dateText = remember(weight.dayKey) {
        BodyWeightDayKey.date(weight.dayKey)?.atZone(ZoneId.systemDefault())?.format(dateFormatter) ?: weight.dayKey
    }
    RevealingSwipeToDeleteRow(
        id = weight.dayKey,
        openRowId = openSwipeRowId,
        onOpenRowChange = onOpenRowChange,
        onDeleteRequested = { viewModel.onDeleteRequested(weight) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = SectionRowMinHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = dateText)
            Text(text = viewModel.unit.formatted(weight.kilograms), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadFailedState(onRetry: () -> Unit) {
    SectionCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.weightHistory_error_loadFailed),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_button_retry)) }
        }
    }
}

@Composable
private fun EmptyState() {
    SectionCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(R.string.weightHistory_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.weightHistory_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
