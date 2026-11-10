package antoni.kalorie.features.dailygoals

import android.view.ContextThemeWrapper
import android.widget.NumberPicker
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.BadgeButton
import antoni.kalorie.components.NumericRowTextField
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.components.appSegmentedButtonColors
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.models.GoalActivityLevel
import antoni.kalorie.core.models.GoalLimits
import antoni.kalorie.core.models.GoalMacroMode
import antoni.kalorie.core.models.GoalSex
import antoni.kalorie.core.models.GoalVariant
import antoni.kalorie.features.dashboard.WeightPickerView
import antoni.kalorie.features.dashboard.applyTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

private const val KCAL_SCROLL_DELAY_MILLIS = 350L
private const val COLLAPSED_CHEVRON_ROTATION = 90f
private const val MACRO_STEP_BUTTON_SIZE = 28
private val SLIDER_THUMB_INSET = 14.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyGoalsView(viewModel: DailyGoalsViewModel, onBack: () -> Unit) {
    // MARK: - Properties

    val scope = rememberCoroutineScope()
    val didFinish by viewModel.didFinish.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val isEndConfirmationVisible by viewModel.isEndConfirmationVisible.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val kcalFocusRequestCount by viewModel.kcalFocusRequestCount.collectAsState()
    val kcalFocusRequester = remember { FocusRequester() }
    val kcalBringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(Unit) { viewModel.onAppear() }
    LaunchedEffect(didFinish) { if (didFinish) onBack() }
    var handledKcalFocusRequestCount by rememberSaveable { mutableIntStateOf(kcalFocusRequestCount) }
    LaunchedEffect(kcalFocusRequestCount) {
        if (kcalFocusRequestCount == handledKcalFocusRequestCount) return@LaunchedEffect
        handledKcalFocusRequestCount = kcalFocusRequestCount
        kcalFocusRequester.requestFocus()
        delay(KCAL_SCROLL_DELAY_MILLIS)
        kcalBringIntoView.bringIntoView()
    }

    // MARK: - Body

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.goals_navigationTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        KeyboardDoneContainer(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { BodyDataSection(viewModel) }
                item { ActivitySection(viewModel) }
                item { CurrentGoalSection(viewModel, kcalFocusRequester, kcalBringIntoView) }
                item { SaveGoalButton(viewModel) { scope.launch { viewModel.onSaveTapped() } } }
                if (viewModel.hasActiveGoal) {
                    item {
                        PrimaryButton(
                            text = stringResource(R.string.goals_button_end),
                            onClick = { viewModel.isEndConfirmationVisible.value = true },
                            style = PrimaryButtonStyle.DESTRUCTIVE,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }

    alertItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(item.titleRes)) },
            text = item.messageRes?.let { message -> { Text(stringResource(message)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }

    if (isEndConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isEndConfirmationVisible.value = false },
            title = { Text(stringResource(R.string.goals_end_title)) },
            text = { Text(stringResource(R.string.goals_end_message)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isEndConfirmationVisible.value = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isEndConfirmationVisible.value = false
                        scope.launch { viewModel.onEndConfirmed() }
                    },
                ) {
                    Text(stringResource(R.string.goals_button_end), color = AppColors.error)
                }
            },
        )
    }
}

// MARK: - Sections

@Composable
private fun SaveGoalButton(viewModel: DailyGoalsViewModel, onClick: () -> Unit) {
    viewModel.kcalText.collectAsState().value
    viewModel.macroMode.collectAsState().value
    viewModel.proteinText.collectAsState().value
    viewModel.carbsText.collectAsState().value
    viewModel.fatText.collectAsState().value
    viewModel.sex.collectAsState().value
    viewModel.birthYear.collectAsState().value
    viewModel.heightCm.collectAsState().value
    viewModel.weightKilograms.collectAsState().value
    viewModel.activityLevel.collectAsState().value
    viewModel.isSaving.collectAsState().value

    PrimaryButton(
        text = stringResource(R.string.goals_button_save),
        onClick = onClick,
        enabled = viewModel.canSave,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun CurrentGoalSection(viewModel: DailyGoalsViewModel, kcalFocusRequester: FocusRequester, kcalBringIntoView: BringIntoViewRequester) {
    val estimate by viewModel.estimate.collectAsState()
    val macroMode by viewModel.macroMode.collectAsState()
    viewModel.proteinText.collectAsState().value
    viewModel.carbsText.collectAsState().value
    viewModel.fatText.collectAsState().value
    viewModel.kcalText.collectAsState().value
    viewModel.rejectedMacroMode.collectAsState().value
    viewModel.selectedVariantIndex.collectAsState().value
    viewModel.sex.collectAsState().value
    val gramsInPercentMode = viewModel.gramsInPercentMode
    val gramsKcal = viewModel.kcalOfEnteredGramsDifferingFromGoal

    SectionHeader(stringResource(R.string.goals_section_current))
    SectionCard {
        Block { InfoText(stringResource(R.string.goals_footer_current)) }
        SectionCardDivider()
        val currentEstimate = estimate
        if (currentEstimate != null) {
            Block(verticalSpacing = 12.dp) {
                ValueRow(stringResource(R.string.goals_estimate_expenditure), kcalString(currentEstimate.expenditure))
                ValueRow(stringResource(R.string.goals_estimate_bmr), kcalString(currentEstimate.basalRate))
                BulletCaption(stringResource(R.string.goals_estimate_disclaimer))
            }
            SectionCardDivider()
            Block(verticalSpacing = 10.dp) {
                VariantSlider(viewModel, currentEstimate.variants)
                KcalRow(viewModel, kcalFocusRequester, kcalBringIntoView)
            }
            if (viewModel.selectedVariant?.isRaisedToBasalRate == true) {
                SectionCardDivider()
                Block { Caption(stringResource(R.string.goals_variant_raised)) }
            }
        } else {
            Block(verticalSpacing = 8.dp) {
                InfoText(stringResource(R.string.goals_estimate_hint))
                KcalRow(viewModel, kcalFocusRequester, kcalBringIntoView)
            }
        }
        SectionCardDivider()
        Block(verticalSpacing = 12.dp) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GoalMacroMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = macroMode == mode,
                        onClick = { viewModel.onMacroModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = GoalMacroMode.entries.size),
                        colors = appSegmentedButtonColors(),
                        icon = {},
                    ) {
                        Text(stringResource(if (mode == GoalMacroMode.PERCENT) R.string.goals_macroMode_percent else R.string.goals_macroMode_grams))
                    }
                }
            }
            MacroRow(
                title = stringResource(R.string.foodQuantity_macro_protein),
                text = viewModel.proteinText.collectAsState().value,
                grams = gramsInPercentMode?.protein,
                macroMode = macroMode,
                onTextChange = viewModel::onProteinTextChanged,
                onStep = { viewModel.onStep(GoalMacro.PROTEIN, it) },
            )
            MacroRow(
                title = stringResource(R.string.foodQuantity_macro_carbs),
                text = viewModel.carbsText.collectAsState().value,
                grams = gramsInPercentMode?.carbs,
                macroMode = macroMode,
                onTextChange = viewModel::onCarbsTextChanged,
                onStep = { viewModel.onStep(GoalMacro.CARBS, it) },
            )
            MacroRow(
                title = stringResource(R.string.foodQuantity_macro_fat),
                text = viewModel.fatText.collectAsState().value,
                grams = gramsInPercentMode?.fat,
                macroMode = macroMode,
                onTextChange = viewModel::onFatTextChanged,
                onStep = { viewModel.onStep(GoalMacro.FAT, it) },
            )
            if (viewModel.isPercentSumInvalid) {
                Text(stringResource(R.string.goals_error_percentSum), style = MaterialTheme.typography.bodySmall, color = AppColors.error)
            }
            if (gramsKcal != null) {
                BulletCaption(stringResource(R.string.goals_gramsKcal, NumberFormat.getIntegerInstance().format(gramsKcal.roundToInt())))
            }
        }
    }
}

@Composable
private fun BodyDataSection(viewModel: DailyGoalsViewModel) {
    val isCollapsed by viewModel.isBodyDataCollapsed.collectAsState()
    val sex by viewModel.sex.collectAsState()
    val birthYear by viewModel.birthYear.collectAsState()
    val heightCm by viewModel.heightCm.collectAsState()
    val weightKilograms by viewModel.weightKilograms.collectAsState()
    val isBirthYearPickerVisible by viewModel.isBirthYearPickerVisible.collectAsState()
    val isHeightPickerVisible by viewModel.isHeightPickerVisible.collectAsState()
    val isWeightPickerVisible by viewModel.isWeightPickerVisible.collectAsState()
    val sexMale = stringResource(R.string.goals_sex_male)
    val sexFemale = stringResource(R.string.goals_sex_female)

    CollapsibleHeader(
        title = stringResource(R.string.goals_section_body),
        isCollapsible = viewModel.isBodyDataRemembered,
        isCollapsed = isCollapsed,
        onToggle = viewModel::onBodyDataToggled,
    )
    SectionCard {
        if (isCollapsed) {
            val summary = listOfNotNull(
                sex?.let { if (it == GoalSex.MALE) sexMale else sexFemale },
                birthYear?.toString(),
                heightCm?.let { viewModel.heightLabel(it) },
                weightKilograms?.let { viewModel.unit.formatted(it) },
            ).joinToString(" · ")
            SummaryRow(summary, onClick = viewModel::onBodyDataToggled)
        } else {
            Block(verticalSpacing = 12.dp) {
                InfoText(stringResource(R.string.goals_footer_body))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GoalSex.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = sex == value,
                            onClick = { viewModel.onSexChanged(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = GoalSex.entries.size),
                            colors = appSegmentedButtonColors(),
                            icon = {},
                        ) {
                            Text(if (value == GoalSex.MALE) sexMale else sexFemale)
                        }
                    }
                }
            }
            SectionCardDivider()
            WheelRow(
                title = stringResource(R.string.goals_field_birthYear),
                value = birthYear?.toString(),
                isVisible = isBirthYearPickerVisible,
                onTap = viewModel::onBirthYearTapped,
                onDismiss = { viewModel.isBirthYearPickerVisible.value = false },
            ) {
                val years = remember(viewModel.currentYear) { viewModel.birthYears }
                WheelPicker(
                    options = years,
                    selection = birthYear ?: (viewModel.currentYear - DEFAULT_PICKER_AGE),
                    label = { it.toString() },
                    onSelect = viewModel::onBirthYearChanged,
                )
            }
            SectionCardDivider()
            WheelRow(
                title = stringResource(R.string.goals_field_height),
                value = heightCm?.let { viewModel.heightLabel(it) },
                isVisible = isHeightPickerVisible,
                onTap = viewModel::onHeightTapped,
                onDismiss = { viewModel.isHeightPickerVisible.value = false },
            ) {
                val heights = remember { GoalLimits.heightRangeCm.toList() }
                WheelPicker(
                    options = heights,
                    selection = heightCm ?: DEFAULT_PICKER_HEIGHT_CM,
                    label = { viewModel.heightLabel(it) },
                    onSelect = viewModel::onHeightChanged,
                )
            }
            SectionCardDivider()
            WheelRow(
                title = stringResource(R.string.goals_field_weight),
                value = weightKilograms?.let { viewModel.unit.formatted(it) },
                isVisible = isWeightPickerVisible,
                onTap = viewModel::onWeightTapped,
                onDismiss = { viewModel.isWeightPickerVisible.value = false },
            ) {
                WeightPickerView(
                    kilograms = weightKilograms ?: antoni.kalorie.core.utils.Constants.BodyWeight.DEFAULT_KILOGRAMS,
                    unit = viewModel.unit,
                    onKilogramsChange = viewModel::onWeightChanged,
                )
            }
        }
    }
}

@Composable
private fun ActivitySection(viewModel: DailyGoalsViewModel) {
    val isCollapsed by viewModel.isActivityCollapsed.collectAsState()
    val activityLevel by viewModel.activityLevel.collectAsState()
    val levels = GoalActivityLevel.entries

    CollapsibleHeader(
        title = stringResource(R.string.goals_section_activity),
        isCollapsible = viewModel.isActivityRemembered,
        isCollapsed = isCollapsed,
        onToggle = viewModel::onActivityToggled,
    )
    SectionCard {
        if (isCollapsed) {
            SummaryRow(activityLevel?.let { stringResource(it.titleRes) } ?: "—", onClick = viewModel::onActivityToggled)
        } else {
            Block(verticalSpacing = 4.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Caption(stringResource(R.string.goals_activity_scaleLow))
                    Caption(stringResource(R.string.goals_activity_scaleHigh))
                }
                Slider(
                    value = levels.indexOf(activityLevel ?: GoalActivityLevel.LIGHT).coerceAtLeast(0).toFloat(),
                    onValueChange = { value -> levels.getOrNull(value.roundToInt())?.let(viewModel::onActivityLevelChanged) },
                    valueRange = 0f..(levels.size - 1).toFloat(),
                    steps = levels.size - 2,
                )
                activityLevel?.let { level ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(level.titleRes), style = MaterialTheme.typography.titleMedium)
                        Caption(stringResource(level.detailRes))
                    }
                }
            }
        }
    }
}

// MARK: - Rows

@Composable
private fun VariantSlider(viewModel: DailyGoalsViewModel, variants: List<GoalVariant>) {
    val selectedIndex by viewModel.selectedVariantIndex.collectAsState()
    viewModel.kcalText.collectAsState().value
    viewModel.estimate.collectAsState().value
    val matchingIndex = viewModel.matchingVariantIndex
    val maintainTitle = stringResource(R.string.goals_variant_maintain)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SliderLabels(inset = SLIDER_THUMB_INSET) {
            variants.forEachIndexed { index, variant ->
                Text(
                    text = variant.shortTitle(maintainTitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (matchingIndex == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = (matchingIndex ?: selectedIndex.toInt()).toFloat(),
            onValueChange = { viewModel.onVariantSelected(it.roundToInt()) },
            onValueChangeFinished = viewModel::onVariantSliderReleased,
            valueRange = 0f..(variants.size - 1).toFloat(),
            steps = variants.size - 2,
        )
    }
}

@Composable
private fun KcalRow(viewModel: DailyGoalsViewModel, focusRequester: FocusRequester, bringIntoView: BringIntoViewRequester) {
    val kcalText by viewModel.kcalText.collectAsState()
    viewModel.rejectedMacroMode.collectAsState().value
    val hint = viewModel.macroModeHint

    Column(modifier = Modifier.bringIntoViewRequester(bringIntoView), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.goals_field_newKcal), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            NumericRowTextField(
                value = kcalText,
                onValueChange = viewModel::onKcalTextChanged,
                placeholder = viewModel.kcalPlaceholder,
                modifier = Modifier.width(120.dp).focusRequester(focusRequester),
                suffix = {
                    Text(stringResource(R.string.common_unit_kcal), color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
        if (hint != null) {
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = AppColors.error)
        }
    }
}

@Composable
private fun MacroRow(
    title: String,
    text: String,
    grams: Double?,
    macroMode: GoalMacroMode,
    onTextChange: (String) -> Unit,
    onStep: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (grams != null) {
            Text(grams.formattedGrams(fractionDigits = 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BadgeButton(
            icon = Icons.Filled.Remove,
            onClick = { onStep(-1) },
            contentDescription = null,
            size = MACRO_STEP_BUTTON_SIZE.dp,
        )
        NumericRowTextField(value = text, onValueChange = onTextChange, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
        BadgeButton(
            icon = Icons.Filled.Add,
            onClick = { onStep(1) },
            contentDescription = null,
            size = MACRO_STEP_BUTTON_SIZE.dp,
        )
        Text(
            text = if (macroMode == GoalMacroMode.PERCENT) "%" else stringResource(R.string.common_unit_grams),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp),
        )
    }
}

@Composable
private fun WheelRow(
    title: String,
    value: String?,
    isVisible: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    picker: @Composable () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp)
                .height(SectionRowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
        }
        DropdownMenu(expanded = isVisible, onDismissRequest = onDismiss, shape = RoundedCornerShape(12.dp)) {
            Box(modifier = Modifier.width(280.dp).padding(16.dp), contentAlignment = Alignment.Center) { picker() }
        }
    }
}

@Composable
private fun WheelPicker(options: List<Int>, selection: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val labels = remember(options) { options.map(label).toTypedArray() }
    val currentOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    AndroidView(
        modifier = Modifier.width(120.dp).height(WHEEL_HEIGHT),
        factory = { context ->
            NumberPicker(ContextThemeWrapper(context, R.style.WeightPickerTheme)).apply {
                minValue = 0
                maxValue = options.lastIndex
                displayedValues = labels
                wrapSelectorWheel = false
                setOnValueChangedListener { _, _, newIndex -> options.getOrNull(newIndex)?.let(currentOnSelect) }
            }
        },
        update = { picker ->
            picker.value = options.indexOf(selection).coerceAtLeast(0)
            picker.applyTextColor(textColor)
        },
    )
}

private val WHEEL_HEIGHT = 160.dp
private const val DEFAULT_PICKER_AGE = 30
private const val DEFAULT_PICKER_HEIGHT_CM = 178

// MARK: - Building blocks

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp),
    )
}

@Composable
private fun CollapsibleHeader(title: String, isCollapsible: Boolean, isCollapsed: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(targetValue = if (isCollapsed) COLLAPSED_CHEVRON_ROTATION else 0f, label = "goalsChevron")
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (isCollapsible) {
            BadgeButton(
                icon = Icons.Filled.KeyboardArrowDown,
                onClick = onToggle,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                size = MACRO_STEP_BUTTON_SIZE.dp,
            )
        }
    }
}

@Composable
private fun SummaryRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    )
}

@Composable
private fun Block(verticalSpacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        content()
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BulletCaption(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Caption(text)
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

@Composable
private fun SliderLabels(inset: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = constraints.maxWidth
        val insetPx = inset.roundToPx()
        val lastIndex = maxOf(placeables.size - 1, 1)
        val travel = width - 2 * insetPx
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val thumbCenter = insetPx + index * travel / lastIndex
                val x = (thumbCenter - placeable.width / 2).coerceIn(0, maxOf(width - placeable.width, 0))
                placeable.place(x, (height - placeable.height) / 2)
            }
        }
    }
}

@Composable
private fun kcalString(kcal: Int): String = "${NumberFormat.getIntegerInstance().format(kcal)} ${stringResource(R.string.common_unit_kcal)}"

private fun GoalVariant.shortTitle(maintainTitle: String): String = when {
    percentChange > 0 -> "+$percentChange %"
    percentChange < 0 -> "$percentChange %"
    else -> maintainTitle
}

private val GoalActivityLevel.titleRes: Int
    @StringRes get() = when (this) {
        GoalActivityLevel.SEDENTARY -> R.string.goals_activity_sedentary
        GoalActivityLevel.LIGHT -> R.string.goals_activity_light
        GoalActivityLevel.MODERATE -> R.string.goals_activity_moderate
        GoalActivityLevel.HIGH -> R.string.goals_activity_high
        GoalActivityLevel.VERY_HIGH -> R.string.goals_activity_veryHigh
    }

private val GoalActivityLevel.detailRes: Int
    @StringRes get() = when (this) {
        GoalActivityLevel.SEDENTARY -> R.string.goals_activity_sedentary_detail
        GoalActivityLevel.LIGHT -> R.string.goals_activity_light_detail
        GoalActivityLevel.MODERATE -> R.string.goals_activity_moderate_detail
        GoalActivityLevel.HIGH -> R.string.goals_activity_high_detail
        GoalActivityLevel.VERY_HIGH -> R.string.goals_activity_veryHigh_detail
    }
