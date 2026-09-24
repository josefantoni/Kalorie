package antoni.kalorie.core.utils

import antoni.kalorie.mealkit.minutesSinceMidnight
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId
    get() = ZoneId.systemDefault()

fun Instant.zoned(): ZonedDateTime = atZone(zone)

fun Instant.minutesSinceMidnight(): Int {
    val time = zoned()
    return minutesSinceMidnight(hour = time.hour, minute = time.minute)
}

fun Instant.isSameDay(other: Instant): Boolean = zoned().toLocalDate() == other.zoned().toLocalDate()

fun Instant.isSameMonth(other: Instant): Boolean {
    val (first, second) = zoned() to other.zoned()
    return first.year == second.year && first.month == second.month
}

fun Instant.formatDateStyle(pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(zoned())

fun Instant.formatCacheKey(pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(zoned())

fun Instant.withAddedMinutes(minutes: Double): Instant = plusMillis((minutes * 60_000).toLong())

fun Instant.withAddedHours(hours: Double): Instant = withAddedMinutes(hours * 60)

fun instantFromEpochSeconds(seconds: Double): Instant = Instant.ofEpochMilli(Math.round(seconds * 1000))

fun Instant.epochSecondsAsDouble(): Double = toEpochMilli() / 1000.0
