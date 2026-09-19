import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfRendererTest {
    private val labels = ExportLabels(
        title = "Příliš žluťoučký kůň úpěl ďábelské ódy",
        columnHeaders = List(12) { "c$it" },
        noEntries = "No entries",
        unassigned = "Unassigned",
        subtotal = "Subtotal",
        dayTotal = "Day total",
        unknown = "–",
        decimalSeparator = ",",
    )

    private fun entry(day: Int) = ExportEntryInput(
        dayIndex = day, sectionId = "b", timestamp = day.toDouble(), name = "Rohlík", amount = "50 g",
        calories = 120, energyKJ = 500.0, protein = 3.0, carbohydrate = 20.0, carbohydrateSugar = 2.0,
        fat = 1.5, fatSaturated = null, fatUnsaturated = 1.0, fiber = null, salt = 0.4,
    )

    private fun report(dayCount: Int, withEntries: Boolean): Report =
        buildReport(
            List(dayCount) { ExportDayInput(it, "Day $it") },
            listOf(ExportSectionInput("b", "Snídaně 07:00–10:00", 1)),
            if (withEntries) List(dayCount) { entry(it) } else emptyList(),
            labels,
        )

    @Test
    fun producesAPdfDocument() {
        val bytes = renderPdf(report(3, withEntries = true))
        assertEquals("%PDF-", bytes.copyOfRange(0, 5).decodeToString())
    }

    @Test
    fun anIntervalWithoutEntriesStillProducesAPdf() {
        val bytes = renderPdf(report(2, withEntries = false))
        assertEquals("%PDF-", bytes.copyOfRange(0, 5).decodeToString())
    }

    @Test
    fun aLongerReportProducesALargerFile() {
        val short = renderPdf(report(2, withEntries = true))
        val long = renderPdf(report(200, withEntries = true))
        assertTrue(long.size > short.size)
    }
}
