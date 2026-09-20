package antoni.kalorie.exportkit

fun buildReport(
    days: List<ExportDayInput>,
    sections: List<ExportSectionInput>,
    entries: List<ExportEntryInput>,
    labels: ExportLabels,
): Report {
    val orderedSections = sections.sortedBy { it.sortKey }
    val entriesByDay = entries.groupBy { it.dayIndex }

    val reportDays = days.sortedBy { it.index }.map { day ->
        val dayEntries = entriesByDay[day.index].orEmpty().sortedBy { it.timestamp }
        if (dayEntries.isEmpty()) {
            ReportDay(label = day.label, sections = emptyList(), total = null)
        } else {
            val reportSections = orderedSections
                .mapNotNull { section ->
                    val sectionEntries = dayEntries.filter { it.sectionId == section.id }
                    section.header.takeIf { sectionEntries.isNotEmpty() }?.let { makeSection(it, sectionEntries) }
                }
                .toMutableList()
            val knownIds = orderedSections.map { it.id }.toSet()
            val unassigned = dayEntries.filter { it.sectionId == null || it.sectionId !in knownIds }
            if (unassigned.isNotEmpty()) {
                reportSections += makeSection(labels.unassigned, unassigned)
            }
            ReportDay(
                label = day.label,
                sections = reportSections,
                total = totalOf(dayEntries),
            )
        }
    }
    return Report(labels = labels, days = reportDays)
}

private fun makeSection(header: String, entries: List<ExportEntryInput>): ReportSection =
    ReportSection(
        header = header,
        rows = entries.map { entry ->
            ReportRow(
                name = entry.name,
                amount = entry.amount,
                calories = entry.calories,
                energyKJ = entry.energyKJ,
                protein = entry.protein,
                carbohydrate = entry.carbohydrate,
                carbohydrateSugar = entry.carbohydrateSugar,
                fat = entry.fat,
                fatSaturated = entry.fatSaturated,
                fatUnsaturated = entry.fatUnsaturated,
                fiber = entry.fiber,
                salt = entry.salt,
            )
        },
        subtotal = totalOf(entries),
    )

private fun totalOf(entries: List<ExportEntryInput>): ReportTotals =
    ReportTotals(
        calories = entries.sumOf { it.calories },
        energyKJ = entries.sumOf { it.energyKJ },
        protein = entries.sumOf { it.protein },
        carbohydrate = entries.sumOf { it.carbohydrate },
        carbohydrateSugar = entries.sumOf { it.carbohydrateSugar },
        fat = entries.sumOf { it.fat },
        fatSaturated = entries.sumOf { it.fatSaturated ?: 0.0 },
        fatUnsaturated = entries.sumOf { it.fatUnsaturated },
        fiber = entries.sumOf { it.fiber ?: 0.0 },
        salt = entries.sumOf { it.salt },
    )
