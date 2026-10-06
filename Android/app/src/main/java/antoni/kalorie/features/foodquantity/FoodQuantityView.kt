package antoni.kalorie.features.foodquantity

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.AlcoholicDrinkHintView
import antoni.kalorie.components.DropdownRowButton
import antoni.kalorie.components.FavouriteButton
import antoni.kalorie.components.NumericRowTextField
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
import antoni.kalorie.components.ReportIncorrectDataMenu
import antoni.kalorie.components.ReportReasonDialog
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.extensions.formattedAmount
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.extensions.formattedTrimmed
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

class MealActions(
    val makeEditorView: @Composable (onBack: () -> Unit) -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodQuantityView(viewModel: FoodQuantityViewModel, onBack: () -> Unit, mealActions: MealActions? = null) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val mealTypes by viewModel.mealTypes.collectAsState()
    val selectedMealTypeId by viewModel.selectedMealTypeId.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val isTogglingFavourite by viewModel.isTogglingFavourite.collectAsState()
    val hasReportedCurrentItem by viewModel.hasReportedCurrentItem.collectAsState()
    val isReportReasonAlertVisible by viewModel.isReportReasonAlertVisible.collectAsState()
    val reportReasonText by viewModel.reportReasonText.collectAsState()
    val isPersonalPortionsManagerPushed by viewModel.isPersonalPortionsManagerPushed.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var quantityText by remember { mutableStateOf(quantity.formattedTrimmed()) }
    var isUnitMenuVisible by remember { mutableStateOf(false) }
    var isMealTypeMenuVisible by remember { mutableStateOf(false) }
    var isMealEditorPushed by remember { mutableStateOf(false) }
    var isDeleteConfirmationVisible by remember { mutableStateOf(false) }
    val item = viewModel.item
    val measure = item.measure

    LaunchedEffect(Unit) { viewModel.onAppear() }
    LaunchedEffect(unit) { quantityText = viewModel.quantity.value.formattedTrimmed() }

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
                        if (mealActions != null) {
                            IconButton(onClick = { isMealEditorPushed = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.myCreatedMeal_button_edit))
                            }
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
                    TextButton(
                        enabled = !state.isLoading,
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch { viewModel.onConfirm() }
                        },
                    ) {
                        Text(stringResource(R.string.foodQuantity_button_add))
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
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        FavouriteButton(isFavourite = isFavourite, isEnabled = !isTogglingFavourite) {
                            scope.launch { viewModel.onFavouriteToggled() }
                        }
                    }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_input_grams)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumericRowTextField(
                                value = quantityText,
                                onValueChange = { text ->
                                    val sanitized = sanitizedQuantityText(text)
                                    quantityText = sanitized
                                    viewModel.quantity.value = sanitized.replace(',', '.').toDoubleOrNull() ?: 0.0
                                },
                                modifier = Modifier.width(96.dp),
                            )
                            Text(
                                text = "×",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            Box {
                                DropdownRowButton(text = unitLabel(unit, measure), onClick = { isUnitMenuVisible = true })
                                DropdownMenu(expanded = isUnitMenuVisible, onDismissRequest = { isUnitMenuVisible = false }) {
                                    for (option in viewModel.unitOptions) {
                                        DropdownMenuItem(
                                            text = { Text(unitLabel(option, measure)) },
                                            onClick = {
                                                isUnitMenuVisible = false
                                                viewModel.onUnitSelected(option)
                                            },
                                        )
                                    }
                                    if (viewModel.isPersonalPortionsAvailable) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.foodQuantity_button_myPortions)) },
                                            onClick = {
                                                isUnitMenuVisible = false
                                                viewModel.onPortionsManagerOpened()
                                                viewModel.isPersonalPortionsManagerPushed.value = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_label_mealType)) {
                        Box {
                            DropdownRowButton(
                                text = mealTypes.firstOrNull { it.id == selectedMealTypeId }?.name
                                    ?: stringResource(R.string.foodQuantity_mealType_unassigned),
                                onClick = { isMealTypeMenuVisible = true },
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
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_calories)) { Text("${viewModel.scaledCalories} kcal") }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_protein)) { Text(viewModel.scaledProtein.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_carbs)) { Text(viewModel.scaledCarbohydrate.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_carbsSugar)) {
                        Text(viewModel.scaledCarbohydrateSugar.formattedGrams())
                    }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_fat)) { Text(viewModel.scaledFat.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_fatSaturated)) { Text(viewModel.scaledFatSaturated.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.foodQuantity_macro_fiber)) { Text(viewModel.scaledFiber.formattedGrams()) }
                    SectionCardDivider()
                    LabeledRow(label = stringResource(R.string.addFood_field_salt)) { Text(viewModel.scaledSalt.formattedGrams(fractionDigits = 2)) }
                }

                if (mealActions != null) {
                    PrimaryButton(
                        text = stringResource(R.string.myCreatedMeal_button_delete),
                        onClick = { isDeleteConfirmationVisible = true },
                        style = PrimaryButtonStyle.DESTRUCTIVE,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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

    if (isPersonalPortionsManagerPushed) {
        FoodPortionsManagerView(viewModel = viewModel, onBack = { viewModel.isPersonalPortionsManagerPushed.value = false })
    }

    if (isMealEditorPushed && mealActions != null) {
        Dialog(
            onDismissRequest = { isMealEditorPushed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            mealActions.makeEditorView { isMealEditorPushed = false }
        }
    }

    if (isDeleteConfirmationVisible && mealActions != null) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmationVisible = false },
            title = { Text(stringResource(R.string.myCreatedMeal_confirm_delete)) },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmationVisible = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmationVisible = false
                        mealActions.onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.myCreatedMeal_button_delete))
                }
            },
        )
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

    alertItem?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(alert.titleRes)) },
            text = alert.messageRes?.let { messageRes -> { Text(stringResource(messageRes)) } },
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

@Composable
private fun unitLabel(unit: FoodQuantityUnit, measure: FoodMeasure): String = when (unit) {
    FoodQuantityUnit.Grams -> stringResource(
        if (measure == FoodMeasure.GRAMS) R.string.foodQuantity_unit_grams else R.string.foodQuantity_unit_millilitres,
    )
    FoodQuantityUnit.HundredGrams -> stringResource(
        if (measure == FoodMeasure.GRAMS) R.string.foodQuantity_unit_hundredGrams else R.string.foodQuantity_unit_hundredMillilitres,
    )
    is FoodQuantityUnit.Portion -> "${unit.portion.name} (${unit.portion.grams.formattedAmount(measure, minFractionDigits = 0)})"
}

private fun sanitizedQuantityText(text: String): String {
    var seenSeparator = false
    return text.filter { char ->
        if (char == '.' || char == ',') {
            if (seenSeparator) return@filter false
            seenSeparator = true
            true
        } else {
            char in '0'..'9'
        }
    }
}
