package antoni.kalorie.features.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.BadgeButton
import kotlinx.coroutines.launch

private const val CHECKMARK_SCALE_FROM = 0.6f
private val CONFIRM_BUTTON_WIDTH = 120.dp

@Composable
fun WeightAccessoryView(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    // MARK: - Properties

    val hasLoadedSettings by viewModel.hasLoadedSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val recentWeights by viewModel.recentWeights.collectAsState()
    val laterWeights by viewModel.laterWeights.collectAsState()
    val shownWeightsKey by viewModel.shownWeightsKey.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val isPopoverVisible by viewModel.isWeightPopoverVisible.collectAsState()
    val unit = appSettings.bodyWeightUnit

    if (!hasLoadedSettings || !appSettings.showBodyWeight) return

    val latest = recentWeights.firstOrNull()
    val canLogWeight = remember(shownWeightsKey, selectedDay) { viewModel.canLogWeight }
    val hasWeightForSelectedDay = remember(recentWeights, selectedDay) { viewModel.hasWeightForSelectedDay }
    val accessibilityLabel = if (latest == null) {
        stringResource(R.string.dashboard_weight_a11yEmpty)
    } else {
        stringResource(R.string.dashboard_weight_a11yValue, unit.formatted(latest.kilograms))
    }

    // MARK: - Body

    Box(modifier = modifier) {
        WeightCircleView(
            weights = recentWeights,
            laterWeights = laterWeights,
            unit = unit,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = viewModel::onWeightCircleTapped)
                .semantics { contentDescription = accessibilityLabel },
        )
        if (canLogWeight) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) {
                BadgeButton(
                    icon = if (hasWeightForSelectedDay) Icons.Filled.Edit else Icons.Filled.Add,
                    onClick = viewModel::onWeightBadgeTapped,
                    contentDescription = stringResource(
                        if (hasWeightForSelectedDay) R.string.dashboard_weight_badgeEdit else R.string.dashboard_weight_badgeLog,
                    ),
                    size = 28.dp,
                )
                WeightPopover(viewModel = viewModel, isVisible = isPopoverVisible)
            }
        }
    }
}

// MARK: - Functions

@Composable
private fun WeightPopover(viewModel: DashboardViewModel, isVisible: Boolean) {
    val appSettings by viewModel.appSettings.collectAsState()
    val kilograms by viewModel.weightPickerKilograms.collectAsState()
    val isSaving by viewModel.isSavingWeight.collectAsState()
    val showCheckmark by viewModel.showWeightCheckmark.collectAsState()
    val scope = rememberCoroutineScope()
    val isBusy = isSaving || showCheckmark

    DropdownMenu(
        expanded = isVisible,
        onDismissRequest = { if (!isBusy) viewModel.isWeightPopoverVisible.value = false },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.width(280.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WeightPickerView(
                kilograms = kilograms,
                unit = appSettings.bodyWeightUnit,
                onKilogramsChange = { viewModel.weightPickerKilograms.value = it },
                isEnabled = !isBusy,
            )
            val containerColor by animateColorAsState(
                targetValue = if (showCheckmark) AppColors.success else MaterialTheme.colorScheme.primary,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                label = "weightConfirmColor",
            )
            val buttonWidth by animateDpAsState(
                targetValue = if (showCheckmark) ButtonDefaults.MinHeight else CONFIRM_BUTTON_WIDTH,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "weightConfirmWidth",
            )
            Button(
                onClick = { scope.launch { viewModel.onWeightConfirmed() } },
                enabled = !isSaving,
                modifier = Modifier.height(ButtonDefaults.MinHeight).width(buttonWidth),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    disabledContainerColor = containerColor.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                AnimatedContent(
                    targetState = showCheckmark,
                    transitionSpec = {
                        (scaleIn(initialScale = CHECKMARK_SCALE_FROM, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                            (scaleOut(targetScale = CHECKMARK_SCALE_FROM) + fadeOut())
                    },
                    label = "weightCheckmark",
                ) { isCheckmarkVisible ->
                    if (isCheckmarkVisible) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    } else {
                        Text(stringResource(R.string.common_button_done))
                    }
                }
            }
        }
    }
}
