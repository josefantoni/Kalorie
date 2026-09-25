package antoni.kalorie.features.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import antoni.kalorie.R
import antoni.kalorie.components.FoodConsumedView
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.isSameDay
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SelectedDayKind {
    PAST,
    TODAY,
    FUTURE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardView(viewModel: DashboardViewModel, router: DashboardRouter) {

    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val mealTypes by viewModel.mealTypes.collectAsState()
    val foodsConsumed by viewModel.foodsConsumed.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val activeDays by viewModel.activeDaysInMonth.collectAsState()
    val showCalendarSheet by viewModel.showCalendarSheet.collectAsState()
    val showMealTypeSheet by viewModel.showMealTypeSheet.collectAsState()
    val showAccountSheet by viewModel.showAccountSheet.collectAsState()
    val showAddFoodSheet by viewModel.showAddFoodSheet.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val isDeleteConfirmationVisible by viewModel.isDeleteConfirmationVisible.collectAsState()
    val groupedFoods = remember(mealTypes, foodsConsumed) { viewModel.groupedFoods }
    val dailyMacros = remember(foodsConsumed) { viewModel.dailyMacros }
    val scope = rememberCoroutineScope()
    var macroPopoverIndex by remember { mutableStateOf<Int?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.onAppear() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { viewModel.onRefresh() } }
    DayChangeEffect { scope.launch { viewModel.onRefresh() } }

    // MARK: - Body

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (foodsConsumed.isNotEmpty()) {
                FloatingActionButton(onClick = { viewModel.showAddFoodSheet.value = !viewModel.showAddFoodSheet.value }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dashboard_empty_addFood))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { viewModel.showAccountSheet.value = !viewModel.showAccountSheet.value }) {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = stringResource(R.string.account_navigationTitle),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showMealTypeSheet.value = !viewModel.showMealTypeSheet.value }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.List,
                            contentDescription = stringResource(R.string.dashboard_button_mealLayout),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                DayPickerView(
                    selectedDay = selectedDay,
                    onSelectedDayChange = { viewModel.selectedDay.value = it },
                    activeDays = activeDays,
                    onDayChanged = { day -> scope.launch { viewModel.onDayChanged(day) } },
                    onTapSelectedDay = { viewModel.showCalendarSheet.value = true },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.onRefresh()
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (foodsConsumed.isNotEmpty()) {
                            item { MacroSummaryView(macros = dailyMacros) }
                        }
                        groupedFoods.forEachIndexed { index, group ->
                            item {
                                SectionHeader(
                                    name = group.mealType?.name ?: stringResource(R.string.dashboard_section_unassignedFoods),
                                    foods = group.foods,
                                    isPopoverVisible = macroPopoverIndex == index,
                                    onPopoverRequested = { macroPopoverIndex = index },
                                    onPopoverDismissed = { macroPopoverIndex = null },
                                )
                            }
                            items(group.foods, key = { it.id }) { food ->
                                SwipeToDeleteRow(onDeleteRequested = { viewModel.onDeleteRequested(food) }) {
                                    FoodConsumedView(
                                        food,
                                        modifier = Modifier.clickable {
                                            viewModel.backStack.add(DashboardDestination.FoodConsumedDetail(food))
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (foodsConsumed.isEmpty() && !state.isLoading) {
                        EmptyStateView(
                            selectedDay = selectedDay,
                            onAddFood = { viewModel.showAddFoodSheet.value = !viewModel.showAddFoodSheet.value },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showCalendarSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showCalendarSheet.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            MonthCalendarView(
                selectedDay = selectedDay,
                activeDays = activeDays,
                onDaySelected = { day -> scope.launch { viewModel.onDaySelected(day) } },
                onMonthChanged = { month -> scope.launch { viewModel.onCalendarMonthChanged(month) } },
            )
        }
    }

    if (showAddFoodSheet) {
        router.makeAddFoodSheetView(
            date = selectedDay,
            mealTypes = mealTypes,
            onDismiss = { viewModel.showAddFoodSheet.value = false },
            onFoodSaved = { scope.launch { viewModel.onFoodConsumedUpdated() } },
        )
    }

    if (showAccountSheet) {
        router.makeAccountView(onDismiss = { viewModel.showAccountSheet.value = false })
    }

    if (showMealTypeSheet) {
        router.makeMealTypeSheetView(
            mealTypes = mealTypes,
            onDismiss = { viewModel.showMealTypeSheet.value = false },
            onMealTypesChanged = { scope.launch { viewModel.onMealTypesChanged() } },
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

    if (isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isDeleteConfirmationVisible.value = false },
            title = { Text(stringResource(R.string.dashboard_confirm_deleteFood)) },
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
                    Text(stringResource(R.string.common_button_yes), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

// MARK: - Functions

@Composable
private fun SectionHeader(
    name: String,
    foods: List<FoodConsumedDomain>,
    isPopoverVisible: Boolean,
    onPopoverRequested: () -> Unit,
    onPopoverDismissed: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = onPopoverRequested) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = isPopoverVisible, onDismissRequest = onPopoverDismissed) {
                MealSectionMacroView(name = name, foods = foods)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(onDeleteRequested: () -> Unit, content: @Composable () -> Unit) {
    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentOnDeleteRequested()
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            }
        },
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            content()
        }
    }
}

private const val PULSE_INTERVAL_MILLIS = 3000L
private const val PULSE_DURATION_MILLIS = 700
private const val PULSE_SCALE = 1.2f

@Composable
private fun EmptyStateView(selectedDay: Instant, onAddFood: () -> Unit, modifier: Modifier = Modifier) {
    val now = Instant.now()
    val kind = when {
        selectedDay.isSameDay(now) -> SelectedDayKind.TODAY
        selectedDay < now -> SelectedDayKind.PAST
        else -> SelectedDayKind.FUTURE
    }
    val titleRes = when (kind) {
        SelectedDayKind.TODAY -> R.string.dashboard_empty_title
        SelectedDayKind.PAST -> R.string.dashboard_empty_title_past
        SelectedDayKind.FUTURE -> R.string.dashboard_empty_title_future
    }
    val descriptionRes = when (kind) {
        SelectedDayKind.TODAY -> R.string.dashboard_empty_description
        SelectedDayKind.PAST -> R.string.dashboard_empty_description_past
        SelectedDayKind.FUTURE -> R.string.dashboard_empty_description_future
    }

    val pulseScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(PULSE_INTERVAL_MILLIS)
            pulseScale.animateTo(PULSE_SCALE, tween(PULSE_DURATION_MILLIS, easing = FastOutSlowInEasing))
            launch { pulseScale.animateTo(1f, tween(PULSE_DURATION_MILLIS, easing = FastOutSlowInEasing)) }
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null)
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onAddFood,
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            },
        ) {
            Text(stringResource(R.string.dashboard_empty_addFood))
        }
    }
}

@Composable
private fun DayChangeEffect(onDayChanged: () -> Unit) {
    val context = LocalContext.current
    val currentOnDayChanged by rememberUpdatedState(onDayChanged)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                currentOnDayChanged()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_DATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
}
