//
//  FoodExportReportFactory.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import ExportKit
import Foundation

struct FoodExportReportFactory {

    // MARK: - Properties

    private let calendar: Calendar

    // MARK: - Init

    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }

    // MARK: - Functions

    func makeReport(
        foods: [FoodConsumedDomain],
        mealTypes: [MealTypeDomain],
        from: Date,
        to: Date
    ) -> ExportKit.Report {
        let firstDay = calendar.startOfDay(for: from)
        let lastDay = calendar.startOfDay(for: to)
        var dayStarts: [Date] = []
        var cursor = firstDay
        while
            cursor <= lastDay,
            let next = calendar.date(byAdding: .day, value: 1, to: cursor)
        {
            dayStarts.append(cursor)
            cursor = next
        }
        let dayIndexes = Dictionary(uniqueKeysWithValues: dayStarts.enumerated().map { ($1, $0) })

        let days = dayStarts.enumerated().map { index, start in
            ExportDayInput(index: Int32(index), label: start.formatted(.dateTime.weekday(.wide).day().month(.wide).year()))
        }
        let sections = mealTypes.map { mealType in
            ExportSectionInput(
                id: mealType.id,
                header: "\(mealType.name) \(windowLabel(of: mealType))",
                sortKey: Int32(mealType.startMinutes)
            )
        }
        let entries: [ExportEntryInput] = foods.compactMap { food in
            guard let dayIndex = dayIndexes[calendar.startOfDay(for: food.date)] else { return nil }
            return ExportEntryInput(
                dayIndex: Int32(dayIndex),
                sectionId: mealTypes.resolvedMealTypeId(for: food),
                timestamp: food.date.timeIntervalSince1970,
                name: food.displayName,
                amount: food.weight.formattedAmount(measure: food.measure),
                calories: Int32(food.calories),
                energyKJ: food.energyKJ,
                protein: food.protein,
                carbohydrate: food.carbohydrate,
                carbohydrateSugar: food.carbohydrateSugar,
                fat: food.fat,
                fatSaturated: food.fatSaturated.map { KotlinDouble(value: $0) },
                fatUnsaturated: food.fatUnsaturated,
                fiber: food.fiber.map { KotlinDouble(value: $0) },
                salt: food.salt
            )
        }
        return ReportBuilderKt.buildReport(days: days, sections: sections, entries: entries, labels: makeLabels(from: from, to: to))
    }

    // MARK: - Private

    private func windowLabel(of mealType: MealTypeDomain) -> String {
        "\(MealTypeDomain.clockTime(minutes: mealType.startMinutes))–\(MealTypeDomain.clockTime(minutes: mealType.endMinutes))"
    }

    private func makeLabels(from: Date, to: Date) -> ExportLabels {
        let interval = "\(from.formatted(date: .long, time: .omitted)) – \(to.formatted(date: .long, time: .omitted))"
        return ExportLabels(
            title: L10n.Export.reportTitle(interval),
            columnHeaders: [
                L10n.Export.columnFood,
                L10n.Export.columnAmount,
                L10n.Export.columnCalories,
                L10n.Export.columnEnergyKJ,
                L10n.Export.columnProtein,
                L10n.Export.columnCarbohydrate,
                L10n.Export.columnSugars,
                L10n.Export.columnFat,
                L10n.Export.columnSaturatedFat,
                L10n.Export.columnUnsaturatedFat,
                L10n.Export.columnFibre,
                L10n.Export.columnSalt
            ],
            noEntries: L10n.Export.noEntries,
            unassigned: L10n.Dashboard.sectionUnassignedFoods,
            subtotal: L10n.Export.subtotal,
            dayTotal: L10n.Export.dayTotal,
            unknown: "–",
            decimalSeparator: Locale.current.decimalSeparator ?? "."
        )
    }
}
