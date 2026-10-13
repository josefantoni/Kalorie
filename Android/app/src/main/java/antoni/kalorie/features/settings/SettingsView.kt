package antoni.kalorie.features.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.BadgeButton
import antoni.kalorie.components.FloatingLabelTextField
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.components.popTransitionSpec
import antoni.kalorie.components.predictivePopTransitionSpec
import antoni.kalorie.components.pushTransitionSpec
import antoni.kalorie.components.sheetScrollGuard
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.core.utils.withAddedMinutes
import antoni.kalorie.core.utils.zoned
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(viewModel: SettingsViewModel, router: SettingsRouter, onDismiss: () -> Unit) {
    // MARK: - Properties

    val mealTypes by viewModel.mealTypes.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var isEditing by remember { mutableStateOf(false) }
    var isFeedbackMailUnavailableAlertPresented by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    val isEditingState = rememberUpdatedState(isEditing)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !isEditingState.value },
        ),
        dragHandle = null,
        sheetGesturesEnabled = viewModel.backStack.size <= 1,
    ) {
        NavDisplay(
            modifier = Modifier.sheetScrollGuard(),
            backStack = viewModel.backStack,
            onBack = { viewModel.backStack.removeLastOrNull() },
            transitionSpec = pushTransitionSpec<SettingsDestination>(),
            popTransitionSpec = popTransitionSpec<SettingsDestination>(),
            predictivePopTransitionSpec = predictivePopTransitionSpec<SettingsDestination>(),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { destination ->
                when (destination) {
                    is SettingsDestination.Settings -> NavEntry(destination) {
                        SettingsContent(
                            viewModel = viewModel,
                            isEditing = isEditing,
                            onEditingChange = { isEditing = it },
                            onDismiss = onDismiss,
                            onFeedbackMailUnavailable = { isFeedbackMailUnavailableAlertPresented = true },
                        )
                    }
                    is SettingsDestination.Export -> NavEntry(destination) {
                        router.makeExportView(mealTypes = mealTypes, onBack = { viewModel.backStack.removeLastOrNull() })
                    }
                    is SettingsDestination.ModerationQueue -> NavEntry(destination) {
                        router.makeModerationQueueView { viewModel.backStack.removeLastOrNull() }
                    }
                    is SettingsDestination.ModerationReports -> NavEntry(destination) {
                        router.makeModerationReportsView { viewModel.backStack.removeLastOrNull() }
                    }
                }
            },
        )
    }
    if (isFeedbackMailUnavailableAlertPresented) {
        AlertDialog(
            onDismissRequest = { isFeedbackMailUnavailableAlertPresented = false },
            title = { Text(stringResource(R.string.settings_feedback_noMail_title)) },
            text = {
                SelectionContainer {
                    Text(stringResource(R.string.settings_feedback_noMail_message, Constants.Support.EMAIL))
                }
            },
            confirmButton = {
                TextButton(onClick = { isFeedbackMailUnavailableAlertPresented = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", Constants.Support.EMAIL))) }
                        isFeedbackMailUnavailableAlertPresented = false
                    },
                ) {
                    Text(stringResource(R.string.settings_feedback_noMail_copy))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    viewModel: SettingsViewModel,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onFeedbackMailUnavailable: () -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val mealTypes by viewModel.mealTypes.collectAsState()
    val isAddFormVisible by viewModel.isAddFormVisible.collectAsState()
    val isMaintainer by viewModel.isMaintainer.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val feedbackSubject = stringResource(R.string.settings_button_feedback)

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_navigationTitle)) },
                navigationIcon = {
                    if (!isEditing) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            KeyboardDoneContainer(modifier = Modifier.fillMaxSize().imePadding()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_mealLayout),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = {
                                if (isEditing) {
                                    onEditingChange(false)
                                    scope.launch { viewModel.onSaveReorder() }
                                } else {
                                    onEditingChange(true)
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    if (isEditing) R.string.settings_button_editDone else R.string.settings_button_edit,
                                ),
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            SectionCard {
                                mealTypes.forEachIndexed { index, mealType ->
                                    key(mealType.id) {
                                        SwipeToDeleteRow(
                                            onDeleteRequested = { scope.launch { viewModel.onDelete(index) } },
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                MealTypeItemView(mealType = mealType, modifier = Modifier.weight(1f))
                                                if (isEditing) {
                                                    IconButton(
                                                        onClick = { viewModel.onMove(from = index, to = index - 1) },
                                                        modifier = Modifier.size(36.dp),
                                                        enabled = index > 0,
                                                    ) {
                                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.onMove(from = index, to = index + 2) },
                                                        modifier = Modifier.size(36.dp),
                                                        enabled = index < mealTypes.lastIndex,
                                                    ) {
                                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                                    }
                                                }
                                            }
                                        }
                                        if (index < mealTypes.lastIndex) SectionCardDivider()
                                    }
                                }
                            }
                        }
                        if (isEditing) {
                            item {
                                FooterView(
                                    isAddFormVisible = isAddFormVisible,
                                    viewModel = viewModel,
                                    focusManager = focusManager,
                                    onCreate = { scope.launch { viewModel.onCreateMealType() } },
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = stringResource(R.string.settings_section_other),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 2.dp),
                                )
                            }
                            item {
                                SectionCard {
                                    NavigationRow(
                                        title = stringResource(R.string.export_navigationTitle),
                                        onClick = { viewModel.backStack.add(SettingsDestination.Export) },
                                    )
                                    SectionCardDivider()
                                    NavigationRow(
                                        title = feedbackSubject,
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
                                                putExtra(Intent.EXTRA_EMAIL, arrayOf(Constants.Support.EMAIL))
                                                putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: ActivityNotFoundException) {
                                                onFeedbackMailUnavailable()
                                            }
                                        },
                                    )
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                SectionCard {
                                    SupportRow(onClick = viewModel::onSupportTapped)
                                }
                            }
                            if (isMaintainer) {
                                item {
                                    Text(
                                        text = stringResource(R.string.moderation_section_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 2.dp),
                                    )
                                }
                                item {
                                    SectionCard {
                                        NavigationRow(
                                            title = stringResource(R.string.moderation_queue_title),
                                            onClick = { viewModel.backStack.add(SettingsDestination.ModerationQueue) },
                                        )
                                        SectionCardDivider()
                                        NavigationRow(
                                            title = stringResource(R.string.moderation_reports_title),
                                            onClick = { viewModel.backStack.add(SettingsDestination.ModerationReports) },
                                        )
                                    }
                                }
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
}

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = SectionRowMinHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SupportRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = SectionRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(AppColors.error, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = stringResource(R.string.settings_button_support), fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.settings_support_subtitle),
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

@Composable
private fun FooterView(
    isAddFormVisible: Boolean,
    viewModel: SettingsViewModel,
    focusManager: FocusManager,
    onCreate: () -> Unit,
) {
    if (!isAddFormVisible) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            BadgeButton(icon = Icons.Filled.Add, onClick = viewModel::onShowAddForm, contentDescription = null)
        }
    } else {
        AddMealForm(
            viewModel = viewModel,
            onCreate = {
                onCreate()
                focusManager.clearFocus()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealForm(viewModel: SettingsViewModel, onCreate: () -> Unit) {
    val name by viewModel.newMealName.collectAsState()
    val initialStart = remember { viewModel.newMealStart.value.zoned() }
    val initialEnd = remember { viewModel.newMealEnd.value.zoned() }
    val startState = rememberTimePickerState(initialHour = initialStart.hour, initialMinute = initialStart.minute, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = initialEnd.hour, initialMinute = initialEnd.minute, is24Hour = true)

    var editedState by remember { mutableStateOf<TimePickerState?>(null) }

    LaunchedEffect(startState.hour, startState.minute, endState.hour, endState.minute) {
        val start = viewModel.newMealStart.value.atMinutesOfDay(startState.hour * 60 + startState.minute)
        var end = viewModel.newMealEnd.value.atMinutesOfDay(endState.hour * 60 + endState.minute)
        if (start >= end) {
            end = start.withAddedMinutes(30.0)
            val endMinutes = end.minutesSinceMidnight()
            endState.hour = endMinutes / 60
            endState.minute = endMinutes % 60
        }
        viewModel.newMealStart.value = start
        viewModel.newMealEnd.value = end
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SectionCard {
            FloatingLabelTextField(
                title = stringResource(R.string.settings_field_newMeal_placeholder),
                text = name,
                onTextChange = { viewModel.newMealName.value = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeRow(stringResource(R.string.settings_datePicker_from), startState, Modifier.weight(1f)) { editedState = startState }
                VerticalDivider(modifier = Modifier.height(SectionRowMinHeight / 2).padding(horizontal = 16.dp))
                TimeRow(stringResource(R.string.settings_datePicker_to), endState, Modifier.weight(1f)) { editedState = endState }
            }
        }
        PrimaryButton(
            text = stringResource(R.string.settings_button_create),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
    }

    editedState?.let { state ->
        TimePickerDialog(
            onDismissRequest = { editedState = null },
            confirmButton = {
                TextButton(onClick = { editedState = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            title = {},
        ) {
            TimePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(label: String, state: TimePickerState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.clickable(onClick = onClick).heightIn(min = SectionRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(text = "%d:%02d".format(state.hour, state.minute), color = MaterialTheme.colorScheme.primary)
    }
}

private fun Instant.atMinutesOfDay(minutes: Int): Instant {
    val time = zoned()
    return time.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong()).atZone(time.zone).toInstant()
}
