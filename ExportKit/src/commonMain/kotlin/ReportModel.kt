package antoni.kalorie.exportkit

class ExportDayInput(val index: Int, val label: String)

class ExportSectionInput(val id: String, val header: String, val sortKey: Int)

class ExportEntryInput(
    val dayIndex: Int,
    val sectionId: String?,
    val timestamp: Double,
    val name: String,
    val amount: String,
    val calories: Int,
    val energyKJ: Double,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatSaturated: Double?,
    val fatUnsaturated: Double,
    val fiber: Double?,
    val salt: Double,
)

class ExportLabels(
    val title: String,
    val columnHeaders: List<String>,
    val noEntries: String,
    val unassigned: String,
    val subtotal: String,
    val dayTotal: String,
    val unknown: String,
    val decimalSeparator: String,
)

class ReportRow(
    val name: String,
    val amount: String,
    val calories: Int,
    val energyKJ: Double,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatSaturated: Double?,
    val fatUnsaturated: Double,
    val fiber: Double?,
    val salt: Double,
)

class ReportTotals(
    val calories: Int,
    val energyKJ: Double,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatSaturated: Double,
    val fatUnsaturated: Double,
    val fiber: Double,
    val salt: Double,
)

class ReportSection(
    val header: String,
    val rows: List<ReportRow>,
    val subtotal: ReportTotals,
)

class ReportDay(
    val label: String,
    val sections: List<ReportSection>,
    val total: ReportTotals?,
)

class Report(
    val labels: ExportLabels,
    val days: List<ReportDay>,
)
