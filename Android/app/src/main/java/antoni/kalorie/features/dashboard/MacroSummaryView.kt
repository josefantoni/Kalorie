package antoni.kalorie.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.MacroDonutView
import antoni.kalorie.core.extensions.formattedGrams

@Composable
fun MacroSummaryView(macros: DailyMacros, modifier: Modifier = Modifier, accessory: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroLabel(color = AppColors.protein, name = stringResource(R.string.foodQuantity_macro_protein), value = macros.protein)
            MacroLabel(color = AppColors.carbs, name = stringResource(R.string.foodQuantity_macro_carbs), value = macros.carbs)
            MacroLabel(color = AppColors.fat, name = stringResource(R.string.foodQuantity_macro_fat), value = macros.fat)
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
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
private val MAX_ACCESSORY_GAP = 40.dp
private val MAX_OVERLAY_WIDTH = DONUT_SIZE + (MAX_ACCESSORY_GAP + WEIGHT_CIRCLE_SIZE) * 2 + ACCESSORY_TRAILING_PADDING

// MARK: - Functions

@Composable
private fun MacroLabel(color: Color, name: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dot(color = color, size = 8)
            Text(text = name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = value.formattedGrams(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
