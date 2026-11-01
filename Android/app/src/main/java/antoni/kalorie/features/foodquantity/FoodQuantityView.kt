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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.R
import antoni.kalorie.components.AlcoholicDrinkHintView
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.DropdownRowButton
import antoni.kalorie.components.FavouriteButton
import antoni.kalorie.components.FoodPhotoTransitionHost
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.components.NumericRowTextField
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
import antoni.kalorie.components.ReportIncorrectDataMenu
import antoni.kalorie.components.ReportReasonDialog
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.components.popTransitionSpec
import antoni.kalorie.components.predictivePopTransitionSpec
import antoni.kalorie.components.pushTransitionSpec
import antoni.kalorie.components.rememberFoodPhotoThumbnailState
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.extensions.formattedAmount
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.extensions.formattedTrimmed
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.textkit.parseDecimalInput
import antoni.kalorie.textkit.sanitizeDecimalInput
import kotlinx.coroutines.launch

class MealActions(
    val makeEditorView: @Composable (onBack: () -> Unit) -> Unit,
    val onDelete: () -> Unit,
)

@Composable
fun FoodQuantityView(viewModel: FoodQuantityViewModel, onBack: () -> Unit, mealActions: MealActions? = null) {
    // MARK: - Properties

    val alertItem by viewModel.alertItem.collectAsState()
    val isReportReasonAlertVisible by viewModel.isReportReasonAlertVisible.collectAsState()
    val reportReasonText by viewModel.reportReasonText.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    NavDisplay(
        backStack = viewModel.backStack,
        onBack = { viewModel.backStack.removeLastOrNull() },
        transitionSpec = pushTransitionSpec<FoodQuantityDestination>(),
        popTransitionSpec = popTransitionSpec<FoodQuantityDestination>(),
        predictivePopTransitionSpec = predictivePopTransitionSpec<FoodQuantityDestination>(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { destination ->
            when (destination) {
                is FoodQuantityDestination.Quantity -> NavEntry(destination) {
                    FoodQuantityContent(
                        viewModel = viewModel,
                        onBack = onBack,
                        mealActions = mealActions,
                        onMealEditorTapped = { viewModel.backStack.add(FoodQuantityDestination.MealEditor) },
                    )
                }
                is FoodQuantityDestination.PortionsManager -> NavEntry(destination) {
                    FoodPortionsManagerView(viewModel = viewModel, onBack = { viewModel.backStack.remove(destination) })
                }
                is FoodQuantityDestination.MealEditor -> NavEntry(destination) {
                    mealActions?.makeEditorView { viewModel.backStack.remove(destination) }
                }
            }
        },
    )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodQuantityContent(
    viewModel: FoodQuantityViewModel,
    onBack: () -> Unit,
    mealActions: MealActions?,
    onMealEditorTapped: () -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val mealTypes by viewModel.mealTypes.collectAsState()
    val selectedMealTypeId by viewModel.selectedMealTypeId.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val isTogglingFavourite by viewModel.isTogglingFavourite.collectAsState()
    val hasReportedCurrentItem by viewModel.hasReportedCurrentItem.collectAsState()
    val photoUrl by viewModel.photoUrl.collectAsState()
    val photoState = rememberFoodPhotoThumbnailState(photoUrl)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var quantityText by remember { mutableStateOf(quantity.formattedTrimmed()) }
    var isUnitMenuVisible by remember { mutableStateOf(false) }
    var isMealTypeMenuVisible by remember { mutableStateOf(false) }
    var isDeleteConfirmationVisible by remember { mutableStateOf(false) }
    val item = viewModel.item
    val measure = item.measure

    LaunchedEffect(unit) { quantityText = viewModel.quantity.value.formattedTrimmed() }
    LaunchedEffect(quantity) {
        if ((parseDecimalInput(quantityText) ?: 0.0) != quantity) {
            quantityText = quantity.formattedTrimmed()
        }
    }

    // MARK: - Body

    FoodPhotoTransitionHost(photoState) { photoTitle ->
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = photoTitle,
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                            if (mealActions != null) {
                                IconButton(onClick = onMealEditorTapped) {
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
                                        val sanitized = sanitizeDecimalInput(text)
                                        quantityText = sanitized
                                        viewModel.quantity.value = parseDecimalInput(sanitized) ?: 0.0
                                    },
                                    modifier = Modifier.widthIn(min = 30.dp, max = 96.dp).weight(1f),
                                )
                                Text(
                                    text = "×",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                Box {
                                    DropdownRowButton(text = unitLabel(unit, measure), onClick = { isUnitMenuVisible = true })
                                    DropdownMenu(expanded = isUnitMenuVisible, onDismissRequest = { isUnitMenuVisible = false }, shape = RoundedCornerShape(12.dp)) {
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
                                                    viewModel.backStack.add(FoodQuantityDestination.PortionsManager)
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
                                DropdownMenu(expanded = isMealTypeMenuVisible, onDismissRequest = { isMealTypeMenuVisible = false }, shape = RoundedCornerShape(12.dp)) {
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
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 2.dp),
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
                        LabeledRow(label = stringResource(R.string.foodQuantity_macro_calories)) { Text("${viewModel.scaledCalories} ${stringResource(R.string.common_unit_kcal)}") }
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
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                }
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
                        contentAlignment = Alignment.Center,
                    ) {
                        LoaderIndicator()
                    }
                }
            }
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
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.error),
                ) {
                    Text(stringResource(R.string.myCreatedMeal_button_delete))
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
    is FoodQuantityUnit.Portion -> "${unit.portion.displayName} (${unit.portion.grams.formattedAmount(measure, minFractionDigits = 0)})"
}
