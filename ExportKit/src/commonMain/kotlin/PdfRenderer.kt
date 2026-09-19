import com.conamobile.pdfkmp.dsl.TableRowScope
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp

private val columnWeights = listOf(3.2f, 1.4f, 1f, 1f, 1.1f, 1.2f, 1.2f, 1f, 1.3f, 1.4f, 1f, 1f)

private class PdfCells(val labels: ExportLabels) {
    fun integer(value: Double) = formatNumber(value, 0, labels.decimalSeparator)
    fun decimal(value: Double) = formatNumber(value, 1, labels.decimalSeparator)

    fun row(row: ReportRow): List<String> = listOf(
        row.name,
        row.amount,
        integer(row.calories.toDouble()),
        integer(row.energyKJ),
        decimal(row.protein),
        decimal(row.carbohydrate),
        decimal(row.carbohydrateSugar),
        decimal(row.fat),
        row.fatSaturated?.let(::decimal) ?: labels.unknown,
        decimal(row.fatUnsaturated),
        row.fiber?.let(::decimal) ?: labels.unknown,
        decimal(row.salt),
    )

    fun totals(totals: ReportTotals): List<String> = listOf(
        integer(totals.calories.toDouble()),
        integer(totals.energyKJ),
        decimal(totals.protein),
        decimal(totals.carbohydrate),
        decimal(totals.carbohydrateSugar),
        decimal(totals.fat),
        decimal(totals.fatSaturated),
        decimal(totals.fatUnsaturated),
        decimal(totals.fiber),
        decimal(totals.salt),
    )
}

private const val COLUMN_COUNT = 12
private val dayBackground = PdfColor.fromRgb(0xECEFF1)
private val gridColor = PdfColor.fromRgb(0xCFD8DC)

private fun TableRowScope.numberCells(values: List<String>, bold: Boolean) {
    values.forEach { value ->
        cell(value, horizontalAlignment = HorizontalAlignment.End) { this.bold = bold }
    }
}

fun renderPdf(report: Report): ByteArray {
    val labels = report.labels
    val cells = PdfCells(labels)
    val document = pdf {
        metadata { title = labels.title }
        defaultTextStyle = TextStyle(fontSize = 7.sp)
        defaultPageBreakStrategy = PageBreakStrategy.Slice
        page(size = PageSize.A4.landscape) {
            padding = Padding.symmetric(horizontal = 28.dp, vertical = 28.dp)
            spacing = 8.dp
            text(labels.title) {
                fontSize = 13.sp
                bold = true
            }
            table(
                columns = columnWeights.map { TableColumn.Weight(it) },
                border = TableBorder(color = gridColor, width = 0.5.dp),
                cellPadding = Padding.symmetric(horizontal = 3.dp, vertical = 2.dp),
            ) {
                header {
                    labels.columnHeaders.take(COLUMN_COUNT).forEachIndexed { index, label ->
                        cell(label, horizontalAlignment = if (index == 0) HorizontalAlignment.Start else HorizontalAlignment.End)
                    }
                }
                for (day in report.days) {
                    row(background = dayBackground) {
                        cell(day.label, colSpan = COLUMN_COUNT) {
                            bold = true
                            fontSize = 8.sp
                        }
                    }
                    if (day.sections.isEmpty()) {
                        row { cell(labels.noEntries, colSpan = COLUMN_COUNT) }
                        continue
                    }
                    for (section in day.sections) {
                        row { cell(section.header, colSpan = COLUMN_COUNT) { bold = true } }
                        for (food in section.rows) {
                            row {
                                val values = cells.row(food)
                                cell(values[0])
                                numberCells(values.drop(1), bold = false)
                            }
                        }
                        row {
                            cell(labels.subtotal, colSpan = 2) { bold = true }
                            numberCells(cells.totals(section.subtotal), bold = true)
                        }
                    }
                    day.total?.let { total ->
                        row(background = dayBackground) {
                            cell(labels.dayTotal, colSpan = 2) { bold = true }
                            numberCells(cells.totals(total), bold = true)
                        }
                    }
                }
            }
        }
    }
    return document.toByteArray()
}
