package antoni.kalorie.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.core.utils.formatDateStyle
import antoni.kalorie.core.utils.isSameMonth
import antoni.kalorie.core.utils.zoned
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7

@Composable
fun MonthCalendarView(
    selectedDay: Instant,
    activeDays: Set<Int>,
    onDaySelected: (Instant) -> Unit,
    onMonthChanged: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayedMonth by remember { mutableStateOf(selectedDay) }

    fun changeMonth(value: Long) {
        val newMonth = displayedMonth.zoned().plusMonths(value).toInstant()
        displayedMonth = newMonth
        onMonthChanged(newMonth)
    }

    fun selectDay(day: Int) {
        val zone = ZoneId.systemDefault()
        val dayOnly = displayedMonth.zoned().toLocalDate().withDayOfMonth(day)
        val timeSource = if (dayOnly == LocalDate.now(zone)) Instant.now() else selectedDay
        val time = timeSource.zoned().toLocalTime().withNano(0)
        onDaySelected(dayOnly.atTime(time).atZone(zone).toInstant())
    }

    Column(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthHeader(
            displayedMonth = displayedMonth,
            onPrevious = { changeMonth(-1) },
            onNext = { changeMonth(1) },
        )
        WeekdayHeader()
        DaysGrid(
            displayedMonth = displayedMonth,
            selectedDay = selectedDay,
            activeDays = activeDays,
            onDayTap = ::selectDay,
        )
    }
}

// MARK: - Functions

@Composable
private fun MonthHeader(displayedMonth: Instant, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
        }
        Text(text = displayedMonth.formatDateStyle("MMMM yyyy"), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (weekday in DayOfWeek.entries) {
            Text(
                text = weekday.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DaysGrid(
    displayedMonth: Instant,
    selectedDay: Instant,
    activeDays: Set<Int>,
    onDayTap: (Int) -> Unit,
) {
    val firstOfMonth = displayedMonth.zoned().toLocalDate().withDayOfMonth(1)
    val leadingEmptyCells = firstOfMonth.dayOfWeek.value - 1
    val cells: List<Int?> = List(leadingEmptyCells) { null } + (1..firstOfMonth.lengthOfMonth()).toList()
    val today = Instant.now()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in cells.chunked(DAYS_IN_WEEK)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (index in 0 until DAYS_IN_WEEK) {
                    val day = week.getOrNull(index)
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f).height(44.dp))
                    } else {
                        CalendarDayCell(
                            day = day,
                            isSelected = selectedDay.zoned().dayOfMonth == day && selectedDay.isSameMonth(displayedMonth),
                            isToday = today.zoned().dayOfMonth == day && today.isSameMonth(displayedMonth),
                            hasActivity = day in activeDays,
                            onTap = { onDayTap(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasActivity: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val circleModifier = Modifier.size(32.dp)

    Column(
        modifier = modifier.height(44.dp).clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = when {
                isSelected -> circleModifier.background(accent, CircleShape)
                isToday -> circleModifier.border(BorderStroke(1.5.dp, accent), CircleShape)
                else -> circleModifier
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$day",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .background(if (hasActivity) accent.copy(alpha = 0.7f) else Color.Transparent, CircleShape),
        )
    }
}
