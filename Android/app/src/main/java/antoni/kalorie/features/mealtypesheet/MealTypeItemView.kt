package antoni.kalorie.features.mealtypesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import antoni.kalorie.core.models.MealTypeDomain

@Composable
fun MealTypeItemView(mealType: MealTypeDomain, modifier: Modifier = Modifier) {

    // MARK: - Body

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = mealType.name)
        Text(text = formatTime(mealType))
    }
}

// MARK: - Functions

private fun formatTime(mealType: MealTypeDomain): String {
    val startTime = MealTypeDomain.clockTime(mealType.startMinutes)
    val endTime = MealTypeDomain.clockTime(mealType.endMinutes)
    return "$startTime - $endTime"
}
