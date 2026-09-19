private const val STYLE_TEXT = 0
private const val STYLE_BOLD = 1
private const val STYLE_INTEGER = 2
private const val STYLE_DECIMAL = 3
private const val STYLE_BOLD_INTEGER = 4
private const val STYLE_BOLD_DECIMAL = 5

private const val COLUMN_COUNT = 12

fun escapeXml(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') out.append(ch)
        }
    }
    return out.toString()
}

fun columnLetter(index: Int): String = ('A' + index).toString()

private class SheetBuilder {
    private val rows = StringBuilder()
    private var rowNumber = 0

    fun row(build: RowBuilder.() -> Unit) {
        rowNumber += 1
        val row = RowBuilder(rowNumber)
        row.build()
        rows.append("<row r=\"").append(rowNumber).append("\">").append(row.cells).append("</row>")
    }

    fun xml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<sheetData>$rows</sheetData></worksheet>"
}

private class RowBuilder(private val rowNumber: Int) {
    val cells = StringBuilder()

    fun text(column: Int, value: String, style: Int = STYLE_TEXT) {
        cells.append("<c r=\"").append(columnLetter(column)).append(rowNumber)
            .append("\" t=\"inlineStr\" s=\"").append(style).append("\"><is><t xml:space=\"preserve\">")
            .append(escapeXml(value)).append("</t></is></c>")
    }

    fun number(column: Int, value: Double, style: Int) {
        cells.append("<c r=\"").append(columnLetter(column)).append(rowNumber)
            .append("\" s=\"").append(style).append("\"><v>").append(value).append("</v></c>")
    }
}

private fun RowBuilder.headerRow(labels: List<String>) {
    labels.forEachIndexed { index, label -> text(index, label, STYLE_BOLD) }
}

private fun RowBuilder.values(
    start: Int,
    calories: Int,
    energyKJ: Double,
    protein: Double,
    carbohydrate: Double,
    carbohydrateSugar: Double,
    fat: Double,
    fatSaturated: Double?,
    fatUnsaturated: Double,
    fiber: Double?,
    salt: Double,
    unknown: String,
    bold: Boolean,
) {
    val integer = if (bold) STYLE_BOLD_INTEGER else STYLE_INTEGER
    val decimal = if (bold) STYLE_BOLD_DECIMAL else STYLE_DECIMAL
    val textStyle = if (bold) STYLE_BOLD else STYLE_TEXT
    number(start, calories.toDouble(), integer)
    number(start + 1, energyKJ, integer)
    number(start + 2, protein, decimal)
    number(start + 3, carbohydrate, decimal)
    number(start + 4, carbohydrateSugar, decimal)
    number(start + 5, fat, decimal)
    if (fatSaturated != null) number(start + 6, fatSaturated, decimal) else text(start + 6, unknown, textStyle)
    number(start + 7, fatUnsaturated, decimal)
    if (fiber != null) number(start + 8, fiber, decimal) else text(start + 8, unknown, textStyle)
    number(start + 9, salt, decimal)
}

private fun RowBuilder.totals(start: Int, totals: ReportTotals, unknown: String) =
    values(
        start = start,
        calories = totals.calories,
        energyKJ = totals.energyKJ,
        protein = totals.protein,
        carbohydrate = totals.carbohydrate,
        carbohydrateSugar = totals.carbohydrateSugar,
        fat = totals.fat,
        fatSaturated = totals.fatSaturated,
        fatUnsaturated = totals.fatUnsaturated,
        fiber = totals.fiber,
        salt = totals.salt,
        unknown = unknown,
        bold = true,
    )

fun sheetXml(report: Report): String {
    val labels = report.labels
    val sheet = SheetBuilder()
    sheet.row { text(0, labels.title, STYLE_BOLD) }
    sheet.row { headerRow(labels.columnHeaders.take(COLUMN_COUNT)) }

    for (day in report.days) {
        sheet.row { text(0, day.label, STYLE_BOLD) }
        if (day.sections.isEmpty()) {
            sheet.row { text(0, labels.noEntries) }
            continue
        }
        for (section in day.sections) {
            sheet.row { text(0, section.header, STYLE_BOLD) }
            for (food in section.rows) {
                sheet.row {
                    text(0, food.name)
                    text(1, food.amount)
                    values(
                        start = 2,
                        calories = food.calories,
                        energyKJ = food.energyKJ,
                        protein = food.protein,
                        carbohydrate = food.carbohydrate,
                        carbohydrateSugar = food.carbohydrateSugar,
                        fat = food.fat,
                        fatSaturated = food.fatSaturated,
                        fatUnsaturated = food.fatUnsaturated,
                        fiber = food.fiber,
                        salt = food.salt,
                        unknown = labels.unknown,
                        bold = false,
                    )
                }
            }
            sheet.row {
                text(0, labels.subtotal, STYLE_BOLD)
                totals(2, section.subtotal, labels.unknown)
            }
        }
        day.total?.let { total ->
            sheet.row {
                text(0, labels.dayTotal, STYLE_BOLD)
                totals(2, total, labels.unknown)
            }
        }
    }
    return sheet.xml()
}

private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

private const val CONTENT_TYPES = XML_HEADER +
    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
    "</Types>"

private const val ROOT_RELS = XML_HEADER +
    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
    "</Relationships>"

private const val WORKBOOK = XML_HEADER +
    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
    "<sheets><sheet name=\"Kalorie\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

private const val WORKBOOK_RELS = XML_HEADER +
    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
    "</Relationships>"

private const val STYLES = XML_HEADER +
    "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
    "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.0\"/></numFmts>" +
    "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
    "<cellXfs count=\"6\">" +
    "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
    "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
    "<xf numFmtId=\"1\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>" +
    "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>" +
    "</cellXfs>" +
    "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
    "</styleSheet>"

fun renderXlsx(report: Report): ByteArray =
    zipStored(
        listOf(
            ZipEntryInput("[Content_Types].xml", CONTENT_TYPES.encodeToByteArray()),
            ZipEntryInput("_rels/.rels", ROOT_RELS.encodeToByteArray()),
            ZipEntryInput("xl/workbook.xml", WORKBOOK.encodeToByteArray()),
            ZipEntryInput("xl/_rels/workbook.xml.rels", WORKBOOK_RELS.encodeToByteArray()),
            ZipEntryInput("xl/styles.xml", STYLES.encodeToByteArray()),
            ZipEntryInput("xl/worksheets/sheet1.xml", sheetXml(report).encodeToByteArray()),
        ),
    )
