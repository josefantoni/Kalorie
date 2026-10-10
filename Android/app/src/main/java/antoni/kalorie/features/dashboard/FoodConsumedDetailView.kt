package antoni.kalorie.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AlcoholicDrinkHintView
import antoni.kalorie.components.DropdownRowButton
import antoni.kalorie.components.FavouriteButton
import antoni.kalorie.components.NumericRowTextField
import antoni.kalorie.components.ReportIncorrectDataMenu
import antoni.kalorie.components.ReportReasonDialog
import antoni.kalorie.components.SaveToolbarButton
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.core.utils.zoned
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    val didSelectMealType by viewModel.didSelectMealType.collectAsState()
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
    val hasChanges = remember(weight, mealTypeId, didSelectMealType, state) { viewModel.hasChanges }

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        if (viewModel.canReportIncorrectData) {
                            Box {
                                ReportIncorrectDataMenu(
                                    hasReportedCurrentItem = hasReportedCurrentItem,
                                    onReportTapped = viewModel::onReportIncorrectDataTapped,
                                )
                            }
                        }
                    }
                },
                actions = {
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
        KeyboardDoneContainer(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = SectionRowMinHeight).padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = food.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (isFavourite || catalogueItem != null) {
                            FavouriteButton(isFavourite = isFavourite, isEnabled = !isTogglingFavourite) {
                                scope.launch { viewModel.onFavouriteToggled() }
                            }
                        }
                    }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_weight)) {
                        NumericRowTextField(
                            value = weightText,
                            onValueChange = { text ->
                                val sanitized = sanitizedWeightText(text)
                                weightText = sanitized
                                sanitized.replace(',', '.').toDoubleOrNull()?.let { viewModel.weight.value = it }
                            },
                            suffix = { Text(stringResource(food.measure.unitSymbolRes)) },
                            modifier = Modifier.width(140.dp),
                        )
                    }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodConsumedDetail_label_time)) {
                        Text(food.date.zoned().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                    }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodConsumedDetail_label_mealType)) {
                        Box {
                            DropdownRowButton(
                                text = mealTypes.firstOrNull { it.id == mealTypeId }?.name
                                    ?: stringResource(R.string.foodConsumedDetail_mealType_unassigned),
                                onClick = { isMealTypeMenuVisible = true },
                                showsArrow = false,
                            )
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.foodQuantity_section_nutrition),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (viewModel.isAlcoholicDrink) {
                        AlcoholicDrinkHintView(formattedAlcoholByVolume = viewModel.formattedAlcoholByVolume)
                    }
                }
                SectionCard {
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_calories)) { Text("${macros.calories} kcal") }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_protein)) { Text(macros.protein.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_carbs)) { Text(macros.carbohydrate.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_carbsSugar)) { Text(macros.carbohydrateSugar.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_fat)) { Text(macros.fat.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_fatSaturated)) { Text(macros.fatSaturated.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_fiber)) { Text(macros.fiber.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_salt)) { Text(macros.salt.formattedGrams(fractionDigits = 2)) }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = SectionRowMinHeight).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        content()
    }
}

private fun initialWeightText(weight: Double): String = if (weight % 1 == 0.0) weight.toLong().toString() else weight.toString()

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
