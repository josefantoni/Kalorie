package antoni.kalorie.core.usecases

import antoni.kalorie.R
import antoni.kalorie.core.extensions.formatted
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.models.resolvedMealTypeId
import antoni.kalorie.core.utils.StringProvider
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.exportkit.ExportDayInput
import antoni.kalorie.exportkit.ExportEntryInput
import antoni.kalorie.exportkit.ExportLabels
import antoni.kalorie.exportkit.ExportSectionInput
import antoni.kalorie.exportkit.Report
import antoni.kalorie.exportkit.buildReport
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class FoodExportReportFactory(
    private val strings: StringProvider,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {

    // MARK: - Functions

    fun makeReport(foods: List<FoodConsumedDomain>, mealTypes: List<MealTypeDomain>, from: Instant, to: Instant): Report {
        val firstDay = from.atZone(zone).toLocalDate()
        val lastDay = to.atZone(zone).toLocalDate()
        val dayStarts = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
        val dayIndexes = dayStarts.withIndex().associate { it.value to it.index }
        val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        val days = dayStarts.mapIndexed { index, start -> ExportDayInput(index = index, label = start.format(dayFormatter)) }
        val sections = mealTypes.map { mealType ->
            ExportSectionInput(id = mealType.id, header = "${mealType.name} ${windowLabel(mealType)}", sortKey = mealType.startMinutes)
        }
        val entries = foods.mapNotNull { food ->
            val dayIndex = dayIndexes[food.date.atZone(zone).toLocalDate()] ?: return@mapNotNull null
            ExportEntryInput(
                dayIndex = dayIndex,
                sectionId = mealTypes.resolvedMealTypeId(food, zone),
                timestamp = food.date.epochSecondsAsDouble(),
                name = food.displayName,
                amount = food.weight.formatted(fractionDigits = 1, unitSymbol = strings.getString(food.measure.unitSymbolRes)),
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
            )
        }
        return buildReport(days = days, sections = sections, entries = entries, labels = makeLabels(from, to))
    }

    // MARK: - Private

    private fun windowLabel(mealType: MealTypeDomain): String =
        "${MealTypeDomain.clockTime(mealType.startMinutes)}–${MealTypeDomain.clockTime(mealType.endMinutes)}"

    private fun makeLabels(from: Instant, to: Instant): ExportLabels {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
        val interval = "${from.atZone(zone).format(formatter)} – ${to.atZone(zone).format(formatter)}"
        return ExportLabels(
            title = strings.getString(R.string.export_report_title, interval),
            columnHeaders = listOf(
                R.string.export_column_food,
                R.string.export_column_amount,
                R.string.export_column_calories,
                R.string.export_column_energyKJ,
                R.string.export_column_protein,
                R.string.export_column_carbohydrate,
                R.string.export_column_sugars,
                R.string.export_column_fat,
                R.string.export_column_saturatedFat,
                R.string.export_column_unsaturatedFat,
                R.string.export_column_fibre,
                R.string.export_column_salt,
            ).map { strings.getString(it) },
            noEntries = strings.getString(R.string.export_report_noEntries),
            unassigned = strings.getString(R.string.dashboard_section_unassignedFoods),
            subtotal = strings.getString(R.string.export_report_subtotal),
            dayTotal = strings.getString(R.string.export_report_dayTotal),
            unknown = "–",
            decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator.toString(),
        )
    }
}
