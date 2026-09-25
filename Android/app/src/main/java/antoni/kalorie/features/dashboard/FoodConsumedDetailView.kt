package antoni.kalorie.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.FavouriteButton
import antoni.kalorie.components.ReportIncorrectDataMenu
import antoni.kalorie.components.ReportReasonDialog
import antoni.kalorie.components.SaveToolbarButton
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.zoned
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodConsumedDetailView(viewModel: FoodConsumedDetailViewModel, onBack: () -> Unit) {

    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val weight by viewModel.weight.collectAsState()
    val showCheckmark by viewModel.showCheckmark.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val mealTypeId by viewModel.mealTypeId.collectAsState()
    val mealTypes by viewModel.mealTypes.collectAsState()
    val hasReportedCurrentItem by viewModel.hasReportedCurrentItem.collectAsState()
    val isReportReasonAlertVisible by viewModel.isReportReasonAlertVisible.collectAsState()
    val reportReasonText by viewModel.reportReasonText.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val catalogueItem by viewModel.catalogueItem.collectAsState()
    val isTogglingFavourite by viewModel.isTogglingFavourite.collectAsState()
    val scope = rememberCoroutineScope()
    var weightText by remember { mutableStateOf(initialWeightText(viewModel.weight.value)) }
    var isMealTypeMenuVisible by remember { mutableStateOf(false) }
    val food = viewModel.food
    val macros = viewModel.scaledMacros
    val hasChanges = viewModel.hasChanges

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (viewModel.canReportIncorrectData) {
                        ReportIncorrectDataMenu(hasReportedCurrentItem = hasReportedCurrentItem, onReportTapped = viewModel::onReportIncorrectDataTapped)
                    }
                    SaveToolbarButton(
                        title = stringResource(R.string.foodConsumedDetail_button_save),
                        showCheckmark = showCheckmark,
                        isEnabled = hasChanges && !state.isLoading,
                    ) {
                        scope.launch { viewModel.onSave() }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = food.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFavourite || catalogueItem != null) {
                        FavouriteButton(isFavourite = isFavourite, isEnabled = !isTogglingFavourite) {
                            scope.launch { viewModel.onFavouriteToggled() }
                        }
                    }
                }
                LabeledRow(label = stringResource(R.string.addFood_field_weight)) {
                    TextField(
                        value = weightText,
                        onValueChange = { text ->
                            val sanitized = sanitizedWeightText(text)
                            weightText = sanitized
                            sanitized.replace(',', '.').toDoubleOrNull()?.let { viewModel.weight.value = it }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
                        singleLine = true,
                        suffix = { Text(stringResource(food.measure.unitSymbolRes)) },
                        modifier = Modifier.width(140.dp),
                    )
                }
                LabeledRow(label = stringResource(R.string.foodConsumedDetail_label_time)) {
                    Text(food.date.zoned().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                }
                LabeledRow(label = stringResource(R.string.foodConsumedDetail_label_mealType)) {
                    Box {
                        TextButton(onClick = { isMealTypeMenuVisible = true }) {
                            Text(
                                mealTypes.firstOrNull { it.id == mealTypeId }?.name
                                    ?: stringResource(R.string.foodConsumedDetail_mealType_unassigned),
                            )
                        }
                        DropdownMenu(expanded = isMealTypeMenuVisible, onDismissRequest = { isMealTypeMenuVisible = false }) {
                            for (mealType in mealTypes) {
                                DropdownMenuItem(
                                    text = { Text(mealType.name) },
                                    onClick = {
                                        isMealTypeMenuVisible = false
                                        viewModel.onMealTypeSelected(mealType.id)
                                    },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.foodQuantity_section_nutrition),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                LabeledRow(label = stringResource(R.string.foodQuantity_macro_calories)) { Text("${macros.calories} kcal") }
                LabeledRow(label = stringResource(R.string.foodQuantity_macro_protein)) { Text(macros.protein.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.foodQuantity_macro_carbs)) { Text(macros.carbohydrate.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.addFood_field_carbsSugar)) { Text(macros.carbohydrateSugar.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.foodQuantity_macro_fat)) { Text(macros.fat.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.addFood_field_fatSaturated)) { Text(macros.fatSaturated.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.addFood_field_fatUnsaturated)) { Text(macros.fatUnsaturated.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.foodQuantity_macro_fiber)) { Text(macros.fiber.formattedGrams()) }
                LabeledRow(label = stringResource(R.string.addFood_field_salt)) { Text(macros.salt.formattedGrams(fractionDigits = 2)) }
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

    if (isReportReasonAlertVisible) {
        ReportReasonDialog(
            text = reportReasonText,
            onTextChange = { viewModel.reportReasonText.value = it },
            onDismiss = { viewModel.isReportReasonAlertVisible.value = false },
            onSend = {
                viewModel.isReportReasonAlertVisible.value = false
                scope.launch { viewModel.onReportSubmitted() }
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
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        content()
    }
}

private fun initialWeightText(weight: Double): String =
    if (weight % 1 == 0.0) weight.toLong().toString() else weight.toString()

private fun sanitizedWeightText(text: String): String {
    var seenSeparator = false
    return text.filter { char ->
        if (char == '.' || char == ',') {
            if (seenSeparator) return@filter false
            seenSeparator = true
            true
        } else {
            char.isDigit()
        }
    }
}
