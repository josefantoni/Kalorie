package antoni.kalorie.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.core.utils.formatDateStyle
import antoni.kalorie.core.utils.isSameDay
import antoni.kalorie.core.utils.isSameMonth
import antoni.kalorie.core.utils.zoned
import java.time.Instant

private const val SWIPE_THRESHOLD_DP = 30

@Composable
fun DayPickerView(
    selectedDay: Instant,
    onSelectedDayChange: (Instant) -> Unit,
    activeDays: Set<Int>,
    onDayChanged: (Instant) -> Unit,
    onTapSelectedDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun adjacentDay(offset: Long): Instant = selectedDay.zoned().plusDays(offset).toInstant()

    fun navigate(offset: Long) {
        val newDay = adjacentDay(offset)
        onSelectedDayChange(newDay)
        onDayChanged(newDay)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(selectedDay) {
                var totalDrag = 0f
                val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -threshold) {
                            navigate(1)
                        } else if (totalDrag > threshold) {
                            navigate(-1)
                        }
                    },
                    onHorizontalDrag = { _, delta -> totalDrag += delta },
                )
            },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            DayCard(
                date = adjacentDay(-1),
                isSelected = false,
                selectedDay = selectedDay,
                activeDays = activeDays,
                modifier = Modifier.weight(1f).clickable { navigate(-1) },
            )
            DayCard(
                date = selectedDay,
                isSelected = true,
                selectedDay = selectedDay,
                activeDays = activeDays,
                modifier = Modifier.weight(1f).clickable { onTapSelectedDay() },
            )
            DayCard(
                date = adjacentDay(1),
                isSelected = false,
                selectedDay = selectedDay,
                activeDays = activeDays,
                modifier = Modifier.weight(1f).clickable { navigate(1) },
            )
        }
    }
}

// MARK: - Functions

@Composable
private fun DayCard(
    date: Instant,
    isSelected: Boolean,
    selectedDay: Instant,
    activeDays: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val hasActivity = date.isSameMonth(selectedDay) && date.zoned().dayOfMonth in activeDays

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = date.formatDateStyle("EEE").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val circleModifier = Modifier.size(36.dp)
        Box(
            modifier = when {
                isSelected -> circleModifier.background(accent, CircleShape)
                date.isSameDay(Instant.now()) -> circleModifier.border(BorderStroke(1.5.dp, accent), CircleShape)
                else -> circleModifier
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${date.zoned().dayOfMonth}",
                style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else null,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }

        Box(
            modifier = Modifier
                .size(4.dp)
                .background(if (hasActivity) accent.copy(alpha = 0.6f) else Color.Transparent, CircleShape),
        )
    }
}
