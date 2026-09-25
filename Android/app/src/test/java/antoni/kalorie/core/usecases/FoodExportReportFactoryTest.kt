package antoni.kalorie.core.usecases

import antoni.kalorie.FixtureLoader
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.utils.StringProviderFake
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodExportReportFactoryTest {

    // MARK: - Tests

    @Test
    fun makeReport_listsEveryDayOfTheIntervalEvenWithoutEntries() {
        val report = makeSUT().makeReport(
            foods = listOf(makeFood(id = "1", day = 2, hour = 8)),
            mealTypes = makeMealTypes(),
            from = makeDate(day = 1),
            to = makeDate(day = 3),
        )

        assertEquals(3, report.days.size)
        assertNull(report.days[0].total)
        assertNotNull(report.days[1].total)
        assertNull(report.days[2].total)
    }

    @Test
    fun makeReport_withNoEntriesAtAll_stillReturnsEveryDay() {
        val report = makeSUT().makeReport(foods = emptyList(), mealTypes = makeMealTypes(), from = makeDate(day = 1), to = makeDate(day = 2))

        assertEquals(2, report.days.size)
        assertTrue(report.days.all { it.sections.isEmpty() })
    }

    @Test
    fun makeReport_withFromAndToOnTheSameDay_returnsOneDay() {
        val report = makeSUT().makeReport(foods = emptyList(), mealTypes = emptyList(), from = makeDate(day = 4, hour = 9), to = makeDate(day = 4, hour = 18))

        assertEquals(1, report.days.size)
    }

    @Test
    fun makeReport_pinnedEntryStaysInItsPinnedMealDespiteItsTimeOfDay() {
        val food = makeFood(id = "late", day = 1, hour = 22, mealTypeId = "breakfast")

        val report = makeSUT().makeReport(foods = listOf(food), mealTypes = makeMealTypes(), from = makeDate(day = 1), to = makeDate(day = 1))

        assertEquals(listOf("Breakfast 07:00–10:00"), report.days[0].sections.map { it.header })
    }

    @Test
    fun makeReport_unpinnedEntryResolvesByTimeOfDay() {
        val food = makeFood(id = "lunch", day = 1, hour = 12)

        val report = makeSUT().makeReport(foods = listOf(food), mealTypes = makeMealTypes(), from = makeDate(day = 1), to = makeDate(day = 1))

        assertEquals(listOf("Lunch 11:00–14:00"), report.days[0].sections.map { it.header })
    }

    @Test
    fun makeReport_entryOutsideEveryWindowGoesToTheTrailingUnassignedSection() {
        val foods = listOf(makeFood(id = "night", day = 1, hour = 23), makeFood(id = "breakfast", day = 1, hour = 8))

        val report = makeSUT().makeReport(foods = foods, mealTypes = makeMealTypes(), from = makeDate(day = 1), to = makeDate(day = 1))

        assertEquals(StringProviderFake().getString(R.string.dashboard_section_unassignedFoods), report.days[0].sections.last().header)
        assertEquals(2, report.days[0].sections.size)
    }

    @Test
    fun makeReport_ignoresEntriesOutsideTheInterval() {
        val foods = listOf(makeFood(id = "in", day = 2, hour = 8), makeFood(id = "out", day = 5, hour = 8))

        val report = makeSUT().makeReport(foods = foods, mealTypes = makeMealTypes(), from = makeDate(day = 1), to = makeDate(day = 3))

        assertEquals(1, report.days.flatMap { it.sections }.flatMap { it.rows }.size)
    }

    @Test
    fun makeReport_unknownOptionalNutrientStaysUnknownOnTheRowButCountsAsZeroInTheTotal() {
        val unknown = makeFood(id = "unknown", day = 1, hour = 8, fiber = null)
        val known = makeFood(id = "known", day = 1, hour = 8, fiber = 3.0)

        val report = makeSUT().makeReport(foods = listOf(unknown, known), mealTypes = makeMealTypes(), from = makeDate(day = 1), to = makeDate(day = 1))

        val rows = report.days[0].sections.first().rows
        assertEquals(1, rows.count { it.fiber == null })
        assertEquals(3.0, report.days[0].total?.fiber ?: -1.0, 0.0)
    }

    @Test
    fun makeReport_matchesSharedDayBucketingFixtureCases() {
        val fixture = FixtureLoader.load("export-day-bucketing-cases")
        val zone = ZoneId.of(fixture.getValue("timeZone").jsonPrimitive.content)
        val sut = FoodExportReportFactory(strings = StringProviderFake(), zone = zone, locale = Locale.ENGLISH)

        for (bucketingCase in fixture.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = bucketingCase.getValue("name").jsonPrimitive.content
            val foods = bucketingCase.getValue("entries").jsonArray.mapIndexed { index, entry ->
                makeFood(id = "entry-$index", date = OffsetDateTime.parse(entry.jsonPrimitive.content).toInstant())
            }

            val report = sut.makeReport(
                foods = foods,
                mealTypes = emptyList(),
                from = OffsetDateTime.parse(bucketingCase.getValue("from").jsonPrimitive.content).toInstant(),
                to = OffsetDateTime.parse(bucketingCase.getValue("to").jsonPrimitive.content).toInstant(),
            )

            assertEquals(name, bucketingCase.getValue("expectedDayCount").jsonPrimitive.int, report.days.size)
            val expectedIndexes = bucketingCase.getValue("expectedDayIndexes").jsonArray.map { if (it is JsonNull) null else it.jsonPrimitive.int }
            val actualIndexes = foods.indices.map { index ->
                report.days.indexOfFirst { day -> day.sections.any { section -> section.rows.any { it.name == "entry-$index" } } }.takeIf { it >= 0 }
            }
            assertEquals(name, expectedIndexes, actualIndexes)
        }
    }

    // MARK: - Helpers

    private fun makeSUT(): FoodExportReportFactory = FoodExportReportFactory(strings = StringProviderFake(), zone = ZONE, locale = Locale.ENGLISH)

    private fun makeMealTypes(): List<MealTypeDomain> = listOf(
        MealTypeDomain(id = "lunch", name = "Lunch", startMinutes = 11 * 60, endMinutes = 14 * 60),
        MealTypeDomain(id = "breakfast", name = "Breakfast", startMinutes = 7 * 60, endMinutes = 10 * 60),
    )

    private fun makeDate(day: Int, hour: Int = 0): Instant = ZonedDateTime.of(2026, 9, day, hour, 0, 0, 0, ZONE).toInstant()

    private fun makeFood(id: String, day: Int, hour: Int, mealTypeId: String? = null, fiber: Double? = 1.0): FoodConsumedDomain =
        makeFood(id = id, date = makeDate(day, hour), mealTypeId = mealTypeId, fiber = fiber)

    private fun makeFood(id: String, date: Instant, mealTypeId: String? = null, fiber: Double? = 1.0) = FoodConsumedDomain(
        id = id,
        foodItemId = id,
        foodItemKind = FoodItemKind.CATALOGUE,
        czName = id,
        engName = id,
        weight = 100.0,
        date = date,
        calories = 100,
        caloriesPerHundredGrams = 100.0,
        energyKJ = 400.0,
        protein = 1.0,
        carbohydrate = 1.0,
        carbohydrateSugar = 1.0,
        fat = 1.0,
        fatSaturated = 1.0,
        fatUnsaturated = 1.0,
        fiber = fiber,
        salt = 1.0,
        mealTypeId = mealTypeId,
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    }
}
