package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import antoni.kalorie.core.extensions.formattedAmount
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.displayName

@Composable
fun FoodConsumedView(foodConsumed: FoodConsumedDomain, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(foodConsumed.weight.formattedAmount(measure = foodConsumed.measure, fractionDigits = 0))
        Text(foodConsumed.displayName, modifier = Modifier.weight(1f))
        Text("${foodConsumed.calories} kcal")
    }
}
