package antoni.kalorie.exportkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportBuilderTest {
    private val labels = ExportLabels(
        title = "T",
        columnHeaders = List(12) { "c$it" },
        noEntries = "No entries",
        unassigned = "Unassigned",
        subtotal = "Subtotal",
        dayTotal = "Day total",
        unknown = "–",
        decimalSeparator = ",",
    )

    private val days = listOf(ExportDayInput(0, "Mon"), ExportDayInput(1, "Tue"))
    private val sections = listOf(
        ExportSectionInput("lunch", "Lunch 11:00–14:00", 660),
        ExportSectionInput("breakfast", "Breakfast 07:00–10:00", 420),
    )

    private fun entry(
        day: Int = 0,
        section: String? = "breakfast",
        at: Double = 0.0,
        name: String = "Food",
        calories: Int = 100,
        fatSaturated: Double? = 1.0,
        fiber: Double? = 2.0,
    ) = ExportEntryInput(
        dayIndex = day, sectionId = section, timestamp = at, name = name, amount = "100 g",
        calories = calories, energyKJ = 400.0, protein = 1.0, carbohydrate = 2.0, carbohydrateSugar = 1.0,
        fat = 3.0, fatSaturated = fatSaturated, fatUnsaturated = 2.0, fiber = fiber, salt = 0.5,
    )

    @Test
    fun sectionsFollowSortKeyNotInputOrder() {
        val report = buildReport(days, sections, listOf(entry(section = "lunch"), entry(section = "breakfast")), labels)
        assertEquals(listOf("Breakfast 07:00–10:00", "Lunch 11:00–14:00"), report.days[0].sections.map { it.header })
    }

    @Test
    fun emptyMealSectionsAreOmitted() {
        val report = buildReport(days, sections, listOf(entry(section = "lunch")), labels)
        assertEquals(1, report.days[0].sections.size)
    }

    @Test
    fun unassignedSectionIsLastAndCatchesUnknownIds() {
        val entries = listOf(entry(section = null, at = 1.0), entry(section = "gone", at = 2.0), entry(section = "lunch"))
        val sectionsOfDay = buildReport(days, sections, entries, labels).days[0].sections
        assertEquals(listOf("Lunch 11:00–14:00", "Unassigned"), sectionsOfDay.map { it.header })
        assertEquals(2, sectionsOfDay.last().rows.size)
    }

    @Test
    fun foodsInASectionAreOrderedByTimestamp() {
        val entries = listOf(entry(name = "B", at = 20.0), entry(name = "A", at = 10.0))
        assertEquals(listOf("A", "B"), buildReport(days, sections, entries, labels).days[0].sections[0].rows.map { it.name })
    }

    @Test
    fun dayWithoutEntriesHasNoSectionsAndNoTotal() {
        val report = buildReport(days, sections, listOf(entry(day = 0)), labels)
        assertTrue(report.days[1].sections.isEmpty())
        assertNull(report.days[1].total)
    }

    @Test
    fun intervalWithoutEntriesStillListsEveryDay() {
        val report = buildReport(days, sections, emptyList(), labels)
        assertEquals(listOf("Mon", "Tue"), report.days.map { it.label })
        assertTrue(report.days.all { it.total == null })
    }

    @Test
    fun daysAreChronologicalWhateverTheInputOrder() {
        val report = buildReport(days.reversed(), sections, emptyList(), labels)
        assertEquals(listOf("Mon", "Tue"), report.days.map { it.label })
    }

    @Test
    fun subtotalsAndDayTotalSumAcrossSections() {
        val entries = listOf(entry(calories = 100), entry(calories = 50, at = 1.0), entry(section = "lunch", calories = 200))
        val day = buildReport(days, sections, entries, labels).days[0]
        assertEquals(150, day.sections[0].subtotal.calories)
        assertEquals(200, day.sections[1].subtotal.calories)
        assertEquals(350, day.total?.calories)
    }

    @Test
    fun unknownOptionalNutrientStaysNullOnTheRowAndCountsAsZeroInTotals() {
        val entries = listOf(entry(fatSaturated = null, fiber = null), entry(fatSaturated = 1.5, fiber = 2.5, at = 1.0))
        val section = buildReport(days, sections, entries, labels).days[0].sections[0]
        assertNull(section.rows[0].fatSaturated)
        assertNull(section.rows[0].fiber)
        assertEquals(1.5, section.subtotal.fatSaturated)
        assertEquals(2.5, section.subtotal.fiber)
    }
}
