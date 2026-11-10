package antoni.kalorie.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.BadgeButton
import antoni.kalorie.components.GoalRingView
import antoni.kalorie.core.models.DailyGoalDomain
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GoalAccessoryView(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    // MARK: - Properties

    val hasLoadedSettings by viewModel.hasLoadedSettings.collectAsState()
    val hasLoadedGoals by viewModel.hasLoadedGoals.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    viewModel.goalHistory.collectAsState().value
    viewModel.selectedDay.collectAsState().value
    val foodsConsumed by viewModel.foodsConsumed.collectAsState()
    val isInfoVisible by viewModel.isGoalInfoVisible.collectAsState()

    if (!hasLoadedSettings || !hasLoadedGoals || !appSettings.showDailyGoal) return
    val isCircleVisible = viewModel.isGoalCircleVisible
    if (!isCircleVisible) return

    val macros = DailyMacros(foodsConsumed)
    val goal = viewModel.selectedGoal
    val progress = viewModel.goalProgress
    val accessibilityLabel = if (progress == null) {
        stringResource(R.string.dashboard_goal_a11yEmpty)
    } else {
        stringResource(R.string.dashboard_goal_a11yValue, NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }.format(progress))
    }

    // MARK: - Body

    Box(modifier = modifier) {
        GoalRingView(
            progress = progress,
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { viewModel.onGoalCircleTapped() }
                .semantics { contentDescription = accessibilityLabel },
        )
        Box(modifier = Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 4.dp)) {
            BadgeButton(
                icon = Icons.Outlined.Info,
                onClick = { viewModel.isGoalInfoVisible.value = true },
                contentDescription = stringResource(R.string.dashboard_goal_badge),
                size = 28.dp,
            )
            DropdownMenu(
                expanded = isInfoVisible,
                onDismissRequest = { viewModel.isGoalInfoVisible.value = false },
                shape = RoundedCornerShape(12.dp),
            ) {
                GoalInfoView(
                    goal = goal,
                    eatenKcal = macros.calories,
                    onOpenGoals = {
                        viewModel.isGoalInfoVisible.value = false
                        viewModel.onGoalCircleTapped()
                    },
                    modifier = Modifier.width(280.dp).padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun GoalInfoView(goal: DailyGoalDomain?, eatenKcal: Int, onOpenGoals: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val kcalGoal = goal?.kcalGoal
        if (kcalGoal != null) {
            val goalKcal = kcalGoal.roundToInt()
            val difference = goalKcal - eatenKcal
            InfoRow(stringResource(R.string.goals_info_eaten), kcalText(eatenKcal))
            InfoRow(stringResource(R.string.goals_info_goal), kcalText(goalKcal))
            InfoRow(
                stringResource(if (difference >= 0) R.string.goals_info_remaining else R.string.goals_info_over),
                kcalText(abs(difference)),
            )
        } else {
            Text(text = stringResource(R.string.goals_info_noGoalMessage), style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = stringResource(R.string.goals_info_donutNote), style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onOpenGoals,
            modifier = Modifier.fillMaxWidth().height(POPOVER_BUTTON_HEIGHT),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = Color.White),
        ) {
            Text(
                text = stringResource(if (goal == null) R.string.goals_info_buttonSet else R.string.goals_info_buttonEdit),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// MARK: - Functions

@Composable
private fun kcalText(value: Int): String = "${NumberFormat.getIntegerInstance().format(value)} ${stringResource(R.string.common_unit_kcal)}"

@Composable
private fun InfoRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
