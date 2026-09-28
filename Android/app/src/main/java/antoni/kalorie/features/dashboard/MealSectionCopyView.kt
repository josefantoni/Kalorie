package antoni.kalorie.features.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.utils.zoned
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val copySuccessColor = Color(0xFF34C759)

@Composable
fun MealSectionCopyView(
    viewModel: DashboardViewModel,
    isVisible: Boolean,
    name: String,
    mealType: MealTypeDomain?,
    foods: List<FoodConsumedDomain>,
) {
    // MARK: - Properties

    val mealTypes by viewModel.mealTypes.collectAsState()
    val targetDay by viewModel.copyTargetDay.collectAsState()
    val targetMealTypeId by viewModel.copyTargetMealTypeId.collectAsState()
    val isCopying by viewModel.isCopying.collectAsState()
    val showCheckmark by viewModel.showCopyCheckmark.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val scope = rememberCoroutineScope()
    var isDayPickerVisible by remember { mutableStateOf(false) }
    val isBusy = isCopying || showCheckmark
    val isCopyEnabled = remember(targetDay, targetMealTypeId, selectedDay, isBusy) { viewModel.canCopy(mealType) }

    // MARK: - Body

    DropdownMenu(
        expanded = isVisible,
        onDismissRequest = { if (!isBusy && !isDayPickerVisible) viewModel.copyPopoverIndex.value = null },
    ) {
        Column(
            modifier = Modifier.width(300.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.dashboard_copy_message, name), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.dashboard_copy_day))
                FilledTonalButton(onClick = { isDayPickerVisible = true }, enabled = !isBusy) {
                    Text(remember(targetDay) { formattedDay(targetDay) })
                }
            }

            Text(text = stringResource(R.string.dashboard_copy_meal))
            Column {
                mealTypes.sortedBy { it.startMinutes }.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.id == targetMealTypeId,
                                enabled = !isBusy,
                                role = Role.RadioButton,
                                onClick = { viewModel.copyTargetMealTypeId.value = option.id },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.id == targetMealTypeId, onClick = null, enabled = !isBusy)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option.name)
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { viewModel.copyPopoverIndex.value = null }, enabled = !isBusy) {
                    Text(stringResource(R.string.common_button_cancel))
                }
                Button(
                    onClick = { scope.launch { viewModel.onCopyConfirmed(foods, mealType) } },
                    enabled = isCopyEnabled || showCheckmark,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showCheckmark) copySuccessColor else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Crossfade(targetState = showCheckmark, label = "copyCheckmark") { isCheckmarkVisible ->
                        if (isCheckmarkVisible) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            Text(stringResource(R.string.dashboard_copy_button))
                        }
                    }
                }
            }
        }
    }

    if (isDayPickerVisible) {
        CopyDayPickerDialog(
            initialDay = targetDay,
            onDaySelected = { viewModel.copyTargetDay.value = it },
            onDismiss = { isDayPickerVisible = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyDayPickerDialog(initialDay: Instant, onDaySelected: (Instant) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay.zoned().toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today

            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDaySelected(day.atStartOfDay(initialDay.zoned().zone).toInstant())
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_button_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun formattedDay(day: Instant): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(day.zoned())
