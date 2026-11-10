package antoni.kalorie.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.MacroDonutView
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.models.GoalMacroGrams
import java.text.NumberFormat

@Composable
fun MacroSummaryView(
    macros: DailyMacros,
    modifier: Modifier = Modifier,
    goalGrams: GoalMacroGrams? = null,
    showsGoalPercent: Boolean = true,
    showsGoalBars: Boolean = true,
    leadingAccessory: @Composable () -> Unit = {},
    accessory: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MacroDonutView(
                protein = macros.protein,
                carbs = macros.carbs,
                fat = macros.fat,
                calories = macros.calories,
                size = DONUT_SIZE,
            )
            Box(modifier = Modifier.widthIn(max = MAX_OVERLAY_WIDTH).fillMaxWidth().height(DONUT_SIZE)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = DONUT_SIZE * 20 / 30 - WEIGHT_CIRCLE_SIZE / 2, end = ACCESSORY_TRAILING_PADDING)
                        .size(WEIGHT_CIRCLE_SIZE),
                ) {
                    accessory()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = DONUT_SIZE * 20 / 30 - WEIGHT_CIRCLE_SIZE / 2, start = ACCESSORY_TRAILING_PADDING)
                        .size(WEIGHT_CIRCLE_SIZE),
                ) {
                    leadingAccessory()
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroLabel(
                modifier = Modifier.weight(1f),
                color = AppColors.protein,
                name = stringResource(R.string.foodQuantity_macro_protein),
                value = macros.protein,
                goal = goalGrams?.protein,
                showsGoalPercent = showsGoalPercent,
                showsGoalBars = showsGoalBars,
            )
            MacroLabel(
                modifier = Modifier.weight(1f),
                color = AppColors.carbs,
                name = stringResource(R.string.foodQuantity_macro_carbs),
                value = macros.carbs,
                goal = goalGrams?.carbs,
                showsGoalPercent = showsGoalPercent,
                showsGoalBars = showsGoalBars,
            )
            MacroLabel(
                modifier = Modifier.weight(1f),
                color = AppColors.fat,
                name = stringResource(R.string.foodQuantity_macro_fat),
                value = macros.fat,
                goal = goalGrams?.fat,
                showsGoalPercent = showsGoalPercent,
                showsGoalBars = showsGoalBars,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow(stringResource(R.string.addFood_field_carbsSugar), macros.carbohydrateSugar, AppColors.carbs, Modifier.weight(1f))
                DetailRow(stringResource(R.string.addFood_field_salt), macros.salt, null, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow(stringResource(R.string.addFood_field_fatSaturated), macros.fatSaturated, AppColors.fat, Modifier.weight(1f))
                DetailRow(stringResource(R.string.addFood_field_fiber), macros.fiber, null, Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Properties

private val DONUT_SIZE = 160.dp
private val ACCESSORY_TRAILING_PADDING = 20.dp
private const val MACRO_LABEL_MIN_SCALE = 0.7f
private val MAX_ACCESSORY_GAP = 40.dp
private val MAX_OVERLAY_WIDTH = DONUT_SIZE + (MAX_ACCESSORY_GAP + WEIGHT_CIRCLE_SIZE) * 2 + ACCESSORY_TRAILING_PADDING

// MARK: - Functions

@Composable
private fun MacroLabel(
    modifier: Modifier,
    color: Color,
    name: String,
    value: Double,
    goal: Double?,
    showsGoalPercent: Boolean,
    showsGoalBars: Boolean,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dot(color = color, size = 8)
            Text(text = name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val valueStyle = MaterialTheme.typography.bodyMedium
        val goalScale = MaterialTheme.typography.bodySmall.fontSize.value / valueStyle.fontSize.value
        val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.formattedGrams()) }
                if (goal != null) {
                    withStyle(SpanStyle(fontSize = goalScale.em, color = goalColor)) { append(" / ${goal.formattedGrams()}") }
                }
            },
            style = valueStyle,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
            autoSize = TextAutoSize.StepBased(minFontSize = (valueStyle.fontSize.value * MACRO_LABEL_MIN_SCALE).sp, maxFontSize = valueStyle.fontSize),
        )
        if (goal != null && goal > 0) {
            if (showsGoalBars) {
                GoalProgressBar(progress = value / goal, color = color, modifier = Modifier.padding(top = 8.dp).width(72.dp))
            }
            if (showsGoalPercent) {
                Text(
                    text = stringResource(R.string.goals_macro_percentOfGoal, NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }.format(value / goal)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun GoalProgressBar(progress: Double, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape)) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.0, 1.0).toFloat()).background(color, CircleShape))
    }
}

@Composable
private fun DetailRow(label: String, value: Double, dotColor: Color?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (dotColor != null) Dot(color = dotColor, size = 6, modifier = Modifier.alignBy { it.measuredHeight })
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(text = value.formattedGrams(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Dot(color: Color, size: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size.dp).background(color, CircleShape))
}
