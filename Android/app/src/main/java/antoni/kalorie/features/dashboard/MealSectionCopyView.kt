package antoni.kalorie.features.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.DropdownRowButton
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
    var isMealMenuVisible by remember { mutableStateOf(false) }
    val isBusy = isCopying || showCheckmark
    val isCopyEnabled = remember(targetDay, targetMealTypeId, selectedDay, isBusy) { viewModel.canCopy(mealType) }

    // MARK: - Body

    DropdownMenu(
        expanded = isVisible,
        onDismissRequest = { if (!isBusy && !isDayPickerVisible && !isMealMenuVisible) viewModel.copyPopoverIndex.value = null },
        shape = RoundedCornerShape(12.dp),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.dashboard_copy_meal))
                Box {
                    DropdownRowButton(
                        text = mealTypes.firstOrNull { it.id == targetMealTypeId }?.name.orEmpty(),
                        onClick = { if (!isBusy) isMealMenuVisible = true },
                    )
                    DropdownMenu(expanded = isMealMenuVisible, onDismissRequest = { isMealMenuVisible = false }, shape = RoundedCornerShape(12.dp)) {
                        mealTypes.sortedBy { it.startMinutes }.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    viewModel.copyTargetMealTypeId.value = option.id
                                    isMealMenuVisible = false
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { viewModel.copyPopoverIndex.value = null },
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(R.string.common_button_cancel), modifier = Modifier.widthIn(min = ButtonDefaults.MinWidth))
                }
                Button(
                    onClick = { scope.launch { viewModel.onCopyConfirmed(foods, mealType) } },
                    enabled = isCopyEnabled || showCheckmark,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showCheckmark) AppColors.success else MaterialTheme.colorScheme.primary,
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
