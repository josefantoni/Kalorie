package antoni.kalorie.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.CarbsColor
import antoni.kalorie.components.FatColor
import antoni.kalorie.components.MacroDonutView
import antoni.kalorie.components.ProteinColor
import antoni.kalorie.core.extensions.formattedGrams

@Composable
fun MacroSummaryView(macros: DailyMacros, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MacroDonutView(
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            calories = macros.calories,
            size = 160.dp,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroLabel(color = ProteinColor, name = stringResource(R.string.foodQuantity_macro_protein), value = macros.protein)
            MacroLabel(color = CarbsColor, name = stringResource(R.string.foodQuantity_macro_carbs), value = macros.carbs)
            MacroLabel(color = FatColor, name = stringResource(R.string.foodQuantity_macro_fat), value = macros.fat)
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow(stringResource(R.string.addFood_field_carbsSugar), macros.carbohydrateSugar, CarbsColor, Modifier.weight(1f))
                DetailRow(stringResource(R.string.addFood_field_fatUnsaturated), macros.fatUnsaturated, FatColor, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow(stringResource(R.string.addFood_field_fiber), macros.fiber, null, Modifier.weight(1f))
                DetailRow(stringResource(R.string.addFood_field_salt), macros.salt, null, Modifier.weight(1f))
            }
        }
    }
}

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
        if (dotColor != null) Dot(color = dotColor, size = 6)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = value.formattedGrams(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Dot(color: Color, size: Int) {
    Box(modifier = Modifier.size(size.dp).background(color, CircleShape))
}
