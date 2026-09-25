package antoni.kalorie.core.utils

import antoni.kalorie.mealkit.minutesSinceMidnight
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

private const val NANOS_PER_SECOND = 1_000_000_000L

private val zone: ZoneId
    get() = ZoneId.systemDefault()

fun Instant.zoned(): ZonedDateTime = atZone(zone)

fun Instant.minutesSinceMidnight(zone: ZoneId = ZoneId.systemDefault()): Int {
    val time = atZone(zone)
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

// A Double of seconds has coarser resolution than a nanosecond, so Double -> Instant -> Double is
// lossless here, unlike the millisecond pair above. A stored value that a rule requires to stay
// unchanged (submitted_at) must survive the round trip through the domain type.
fun instantFromEpochSecondsExact(seconds: Double): Instant {
    val wholeSeconds = floor(seconds)
    return Instant.ofEpochSecond(wholeSeconds.toLong(), ((seconds - wholeSeconds) * NANOS_PER_SECOND).roundToLong())
}

fun Instant.epochSecondsAsExactDouble(): Double = epochSecond.toDouble() + nano.toDouble() / NANOS_PER_SECOND
