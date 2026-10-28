package antoni.kalorie.core.models

import antoni.kalorie.FixtureLoader
import antoni.kalorie.core.networking.AppSettingsDTO
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class BodyWeightModelTest {

    // MARK: - Day key

    @Test
    fun dayKey_formatsGregorianDateInTimeZone() {
        val date = Instant.ofEpochSecond(1_792_000_000)
        assertEquals("2026-10-14", BodyWeightDayKey.make(date, ZoneId.of("Europe/Prague")))
    }

    @Test
    fun year_isGregorianAndFollowsTimeZone() {
        val newYearInPrague = Instant.ofEpochSecond(1_798_759_800)
        assertEquals(2027, BodyWeightDayKey.year(newYearInPrague, ZoneId.of("Europe/Prague")))
        assertEquals(2026, BodyWeightDayKey.year(newYearInPrague, ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun dayKey_followsTimeZone() {
        val date = Instant.ofEpochSecond(1_792_015_800)
        assertNotEquals(
            BodyWeightDayKey.make(date, ZoneId.of("Pacific/Auckland")),
            BodyWeightDayKey.make(date, ZoneId.of("America/Los_Angeles")),
        )
    }

    @Test
    fun dayKey_matchesSharedFixtureCases() {
        val fixture = FixtureLoader.load("body-weight-day-key-cases")
        for (dayKeyCase in fixture.getValue("cases").jsonArray.map { it.jsonObject }) {
            val instant = OffsetDateTime.parse(dayKeyCase.getValue("instant").jsonPrimitive.content).toInstant()
            val timeZone = ZoneId.of(dayKeyCase.getValue("timeZone").jsonPrimitive.content)
            assertEquals(
                dayKeyCase.getValue("name").jsonPrimitive.content,
                dayKeyCase.getValue("expectedKey").jsonPrimitive.content,
                BodyWeightDayKey.make(instant, timeZone),
            )
        }
    }

    @Test
    fun date_roundTripsTheKeyAtNoon() {
        val zone = ZoneId.of("Europe/Prague")
        val instant = checkNotNull(BodyWeightDayKey.date("2026-10-27", zone))
        assertEquals("2026-10-27", BodyWeightDayKey.make(instant, zone))
        assertNull(BodyWeightDayKey.date("not-a-day", zone))
    }

    // MARK: - Settings

    @Test
    fun settings_unknownUnit_readsAsKilograms() {
        assertEquals(BodyWeightUnit.LB, AppSettingsDTO(AppSettingsDomain(false, BodyWeightUnit.LB)).asDomain().bodyWeightUnit)
        assertNull(BodyWeightUnit.fromRawValue("stone"))
        assertEquals(BodyWeightUnit.KG, AppSettingsDTO(bodyWeightUnit = "stone").asDomain().bodyWeightUnit)
    }

    @Test
    fun settings_absentFields_readAsDefaults() {
        assertEquals(AppSettingsDomain.defaults, AppSettingsDTO().asDomain())
    }

    // MARK: - Units

    @Test
    fun unit_poundsRoundTripExactlyThroughKilograms() {
        val kilograms = BodyWeightUnit.LB.kilograms(180.0)
        assertEquals(180.0, BodyWeightUnit.LB.display(kilograms), 1e-9)
    }

    @Test
    fun unit_poundPickerRangeIsTheWidestOneThatStaysInsideTheKilogramRange() {
        val range = BodyWeightUnit.LB.rangeInTenths
        fun kilograms(tenths: Int) = BodyWeightUnit.LB.kilograms(tenths / 10.0)

        assertTrue(kilograms(range.first) >= BodyWeightUnit.MIN_KILOGRAMS)
        assertTrue(kilograms(range.last) <= BodyWeightUnit.MAX_KILOGRAMS)
        assertTrue(kilograms(range.first - 1) < BodyWeightUnit.MIN_KILOGRAMS)
        assertTrue(kilograms(range.last + 1) > BodyWeightUnit.MAX_KILOGRAMS)
    }
}
