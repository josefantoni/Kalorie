package antoni.kalorie.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.CarbsColor
import antoni.kalorie.components.FatColor
import antoni.kalorie.components.MacroDonutView
import antoni.kalorie.components.ProteinColor
import antoni.kalorie.core.extensions.formattedGrams
import antoni.kalorie.core.models.FoodConsumedDomain

@Composable
fun MealSectionMacroView(name: String, foods: List<FoodConsumedDomain>, modifier: Modifier = Modifier) {
    val macros = remember(foods) { DailyMacros(foods) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        HorizontalDivider()

        MacroDonutView(
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            calories = macros.calories,
            size = 120.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        HorizontalDivider()
        MacroRow(label = stringResource(R.string.foodQuantity_macro_protein), value = macros.protein.formattedGrams(), dotColor = ProteinColor)
        MacroRow(label = stringResource(R.string.foodQuantity_macro_carbs), value = macros.carbs.formattedGrams(), dotColor = CarbsColor)
        MacroRow(label = stringResource(R.string.addFood_field_carbsSugar), value = macros.carbohydrateSugar.formattedGrams(), dotColor = CarbsColor, isIndented = true)
        MacroRow(label = stringResource(R.string.foodQuantity_macro_fat), value = macros.fat.formattedGrams(), dotColor = FatColor)
        MacroRow(label = stringResource(R.string.addFood_field_fatUnsaturated), value = macros.fatUnsaturated.formattedGrams(), dotColor = FatColor, isIndented = true)
        MacroRow(label = stringResource(R.string.addFood_field_fiber), value = macros.fiber.formattedGrams())
        MacroRow(label = stringResource(R.string.addFood_field_salt), value = macros.salt.formattedGrams())
    }
}

// MARK: - Functions

@Composable
private fun MacroRow(label: String, value: String, dotColor: Color? = null, isIndented: Boolean = false) {
    val style = if (isIndented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isIndented) Spacer(Modifier.width(12.dp))
        Dot(color = dotColor ?: Color.Transparent, size = if (isIndented) 6 else 8)
        Text(text = label, style = style, color = color, modifier = Modifier.weight(1f))
        Text(text = value, style = style, fontWeight = FontWeight.Bold, color = color)
    }
}
