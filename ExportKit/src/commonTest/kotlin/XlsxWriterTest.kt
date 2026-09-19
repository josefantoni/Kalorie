import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XlsxWriterTest {
    private val labels = ExportLabels(
        title = "Kalorie & <co>",
        columnHeaders = List(12) { "c$it" },
        noEntries = "No entries",
        unassigned = "Unassigned",
        subtotal = "Subtotal",
        dayTotal = "Day total",
        unknown = "–",
        decimalSeparator = ",",
    )

    private fun sampleReport(): Report {
        val entry = ExportEntryInput(
            dayIndex = 0, sectionId = "b", timestamp = 0.0, name = "Rohlík \"A&B\" <1>", amount = "50 g",
            calories = 120, energyKJ = 500.0, protein = 3.0, carbohydrate = 20.0, carbohydrateSugar = 2.0,
            fat = 1.5, fatSaturated = null, fatUnsaturated = 1.0, fiber = null, salt = 0.4,
        )
        return buildReport(
            listOf(ExportDayInput(0, "Mon"), ExportDayInput(1, "Tue")),
            listOf(ExportSectionInput("b", "Breakfast", 1)),
            listOf(entry),
            labels,
        )
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = u16(b, o) or (u16(b, o + 2) shl 16)

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val eocd = bytes.size - 22
        assertEquals(0x06054b50, u32(bytes, eocd))
        val count = u16(bytes, eocd + 10)
        var cursor = u32(bytes, eocd + 16)
        val result = mutableMapOf<String, ByteArray>()
        repeat(count) {
            assertEquals(0x02014b50, u32(bytes, cursor))
            val crc = u32(bytes, cursor + 16)
            val size = u32(bytes, cursor + 24)
            val nameLength = u16(bytes, cursor + 28)
            val local = u32(bytes, cursor + 42)
            val name = bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength).decodeToString()
            assertEquals(0x04034b50, u32(bytes, local))
            val dataStart = local + 30 + u16(bytes, local + 26) + u16(bytes, local + 28)
            val data = bytes.copyOfRange(dataStart, dataStart + size)
            assertEquals(crc, crc32(data), "CRC of $name")
            result[name] = data
            cursor += 46 + nameLength
        }
        return result
    }

    @Test
    fun crc32MatchesTheStandardCheckValue() {
        assertEquals(0xCBF43926.toInt(), crc32("123456789".encodeToByteArray()))
    }

    @Test
    fun crc32OfNothingIsZero() {
        assertEquals(0, crc32(ByteArray(0)))
    }

    @Test
    fun zipRoundTripsEveryEntryWithAValidCrc() {
        val entries = listOf(ZipEntryInput("a.txt", "hello".encodeToByteArray()), ZipEntryInput("d/b.txt", ByteArray(0)))
        val read = readZip(zipStored(entries))
        assertEquals("hello", read.getValue("a.txt").decodeToString())
        assertEquals(0, read.getValue("d/b.txt").size)
    }

    @Test
    fun workbookContainsExactlyTheSixRequiredParts() {
        val names = readZip(renderXlsx(sampleReport())).keys
        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml",
            ),
            names,
        )
    }

    @Test
    fun userTextIsXmlEscapedAndDiacriticsSurvive() {
        val sheet = readZip(renderXlsx(sampleReport())).getValue("xl/worksheets/sheet1.xml").decodeToString()
        assertTrue(sheet.contains("Rohlík &quot;A&amp;B&quot; &lt;1&gt;"))
        assertTrue(sheet.contains("Kalorie &amp; &lt;co&gt;"))
        assertTrue(!sheet.contains("<1>"))
    }

    @Test
    fun numbersAreNumericCellsAndUnknownIsTheDashText() {
        val sheet = sheetXml(sampleReport())
        assertTrue(sheet.contains("<c r=\"C5\" s=\"2\"><v>120.0</v></c>"))
        assertTrue(sheet.contains("<c r=\"I5\" t=\"inlineStr\" s=\"0\"><is><t xml:space=\"preserve\">–</t></is></c>"))
        assertTrue(sheet.contains("<c r=\"K5\" t=\"inlineStr\" s=\"0\"><is><t xml:space=\"preserve\">–</t></is></c>"))
    }

    @Test
    fun emptyDayIsAHeaderFollowedByNoEntries() {
        val sheet = sheetXml(sampleReport())
        assertTrue(sheet.contains("Tue</t></is></c></row><row r=\"9\"><c r=\"A9\" t=\"inlineStr\" s=\"0\"><is><t xml:space=\"preserve\">No entries"))
    }

    @Test
    fun totalRowsAreBold() {
        val sheet = sheetXml(sampleReport())
        assertTrue(sheet.contains("<c r=\"A6\" t=\"inlineStr\" s=\"1\"><is><t xml:space=\"preserve\">Subtotal"))
        assertTrue(sheet.contains("<c r=\"A7\" t=\"inlineStr\" s=\"1\"><is><t xml:space=\"preserve\">Day total"))
        assertTrue(sheet.contains("<c r=\"C7\" s=\"4\"><v>120.0</v></c>"))
    }
}
