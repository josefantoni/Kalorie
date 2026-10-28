package antoni.kalorie.core.models

import antoni.kalorie.core.utils.Constants
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class BodyWeightUnit(val rawValue: String) {
    KG("kg"),
    LB("lb"),
    ;

    // MARK: - Properties

    val symbol: String get() = rawValue

    val rangeInTenths: IntRange
        get() = when (this) {
            KG -> 300..3000
            LB -> 662..6613
        }

    // MARK: - Functions

    fun display(kilograms: Double): Double = when (this) {
        KG -> kilograms
        LB -> kilograms * POUNDS_PER_KILOGRAM
    }

    fun kilograms(from: Double): Double = when (this) {
        KG -> from
        LB -> from * Constants.BodyWeight.KILOGRAMS_PER_POUND
    }

    fun formatted(kilograms: Double): String {
        val format = NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return "${format.format(display(kilograms))} $symbol"
    }

    fun tenths(kilograms: Double): Int = Math.round(display(kilograms) * 10).toInt().coerceIn(rangeInTenths.first, rangeInTenths.last)

    companion object {
        const val POUNDS_PER_KILOGRAM = 1 / Constants.BodyWeight.KILOGRAMS_PER_POUND
        const val MIN_KILOGRAMS = 30.0
        const val MAX_KILOGRAMS = 300.0

        fun fromRawValue(rawValue: String): BodyWeightUnit? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

data class BodyWeightDomain(
    val dayKey: String,
    val kilograms: Double,
)

data class BodyWeightWindow(
    val earlier: List<BodyWeightDomain>,
    val later: List<BodyWeightDomain>,
) {

    // MARK: - Properties

    val chronological: List<BodyWeightDomain> get() = earlier.reversed() + later

    companion object {
        val empty = BodyWeightWindow(earlier = emptyList(), later = emptyList())
    }
}

data class AppSettingsDomain(
    val showBodyWeight: Boolean,
    val bodyWeightUnit: BodyWeightUnit,
) {
    companion object {
        val defaults = AppSettingsDomain(showBodyWeight = true, bodyWeightUnit = BodyWeightUnit.KG)
    }
}

object BodyWeightDayKey {

    // MARK: - Properties

    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withChronology(IsoChronology.INSTANCE)

    // MARK: - Functions

    fun make(from: Instant, timeZone: ZoneId = ZoneId.systemDefault()): String = formatter.format(from.atZone(timeZone).toLocalDate())

    fun year(of: Instant, timeZone: ZoneId = ZoneId.systemDefault()): Int = of.atZone(timeZone).year

    fun date(from: String, timeZone: ZoneId = ZoneId.systemDefault()): Instant? {
        val parts = from.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        return runCatching { LocalDate.of(parts[0], parts[1], parts[2]).atTime(NOON, 0).atZone(timeZone).toInstant() }.getOrNull()
    }

    private const val NOON = 12
}
