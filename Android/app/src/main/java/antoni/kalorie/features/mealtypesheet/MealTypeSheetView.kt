package antoni.kalorie.features.mealtypesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.core.utils.withAddedMinutes
import antoni.kalorie.core.utils.zoned
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import java.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTypeSheetView(viewModel: MealTypeSheetViewModel, onDismiss: () -> Unit) {

    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val mealTypes by viewModel.mealTypes.collectAsState()
    val isAddFormVisible by viewModel.isAddFormVisible.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isEditing by remember { mutableStateOf(false) }

    // MARK: - Body

    Dialog(
        onDismissRequest = { if (!isEditing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
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
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.mealTypeSheet_section_mealLayout),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = {
                                if (isEditing) {
                                    isEditing = false
                                    scope.launch { viewModel.onSaveReorder() }
                                } else {
                                    isEditing = true
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    if (isEditing) R.string.mealTypeSheet_button_editDone else R.string.mealTypeSheet_button_edit,
                                ),
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(mealTypes, key = { _, mealType -> mealType.id }) { index, mealType ->
                            SwipeToDeleteRow(onDeleteRequested = { scope.launch { viewModel.onDelete(index) } }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MealTypeItemView(mealType = mealType, modifier = Modifier.weight(1f))
                                    if (isEditing) {
                                        IconButton(onClick = { viewModel.onMove(from = index, to = index - 1) }, enabled = index > 0) {
                                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                                        }
                                        IconButton(
                                            onClick = { viewModel.onMove(from = index, to = index + 2) },
                                            enabled = index < mealTypes.lastIndex,
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                        }
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
private fun FooterView(
    isAddFormVisible: Boolean,
    viewModel: MealTypeSheetViewModel,
    focusManager: FocusManager,
    onCreate: () -> Unit,
) {
    if (!isAddFormVisible) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = viewModel::onShowAddForm) {
                Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
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
private fun AddMealForm(viewModel: MealTypeSheetViewModel, onCreate: () -> Unit) {
    val name by viewModel.newMealName.collectAsState()
    val initialStart = remember { viewModel.newMealStart.value.zoned() }
    val initialEnd = remember { viewModel.newMealEnd.value.zoned() }
    val startState = rememberTimePickerState(initialHour = initialStart.hour, initialMinute = initialStart.minute, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = initialEnd.hour, initialMinute = initialEnd.minute, is24Hour = true)

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

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.newMealName.value = it },
            placeholder = { Text(stringResource(R.string.mealTypeSheet_field_newMeal_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.mealTypeSheet_datePicker_from), style = MaterialTheme.typography.labelMedium)
                TimeInput(state = startState)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.mealTypeSheet_datePicker_to), style = MaterialTheme.typography.labelMedium)
                TimeInput(state = endState)
            }
        }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mealTypeSheet_button_create))
        }
    }
}

private fun Instant.atMinutesOfDay(minutes: Int): Instant {
    val time = zoned()
    return time.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong()).atZone(time.zone).toInstant()
}
