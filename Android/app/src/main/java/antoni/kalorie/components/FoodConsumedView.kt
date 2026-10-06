package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import antoni.kalorie.core.extensions.formattedAmount
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.displayName

@Composable
fun FoodConsumedView(foodConsumed: FoodConsumedDomain, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                foodConsumed.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = MaterialTheme.typography.bodyLarge.fontSize * NAME_MIN_SCALE,
                    maxFontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
            )
            Text(
                foodConsumed.weight.formattedAmount(measure = foodConsumed.measure, fractionDigits = 0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${foodConsumed.calories} kcal",
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            softWrap = false,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val NAME_MIN_SCALE = 0.8f
