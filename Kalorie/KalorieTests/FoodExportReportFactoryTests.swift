//
//  FoodExportReportFactoryTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 19.09.2026.
//

import ExportKit
import XCTest
@testable import Kalorie

final class FoodExportReportFactoryTests: XCTestCase {

    // MARK: - Tests

    func test_makeReport_listsEveryDayOfTheIntervalEvenWithoutEntries() throws {
        let sut = makeSUT()
        let report = sut.makeReport(
            foods: [makeFood(id: "1", day: 2, hour: 8)],
            mealTypes: makeMealTypes(),
            from: try makeDate(day: 1),
            to: try makeDate(day: 3)
        )

        XCTAssertEqual(report.days.count, 3)
        XCTAssertNil(report.days[0].total)
        XCTAssertNotNil(report.days[1].total)
        XCTAssertNil(report.days[2].total)
    }

    func test_makeReport_withNoEntriesAtAll_stillReturnsEveryDay() throws {
        let report = makeSUT().makeReport(foods: [], mealTypes: makeMealTypes(), from: try makeDate(day: 1), to: try makeDate(day: 2))

        XCTAssertEqual(report.days.count, 2)
        XCTAssertTrue(report.days.allSatisfy { $0.sections.isEmpty })
    }

    func test_makeReport_withFromAndToOnTheSameDay_returnsOneDay() throws {
        let report = makeSUT().makeReport(foods: [], mealTypes: [], from: try makeDate(day: 4, hour: 9), to: try makeDate(day: 4, hour: 18))

        XCTAssertEqual(report.days.count, 1)
    }

    func test_makeReport_pinnedEntryStaysInItsPinnedMealDespiteItsTimeOfDay() throws {
        let food = makeFood(id: "late", day: 1, hour: 22, mealTypeId: "breakfast")
        let report = makeSUT().makeReport(foods: [food], mealTypes: makeMealTypes(), from: try makeDate(day: 1), to: try makeDate(day: 1))

        XCTAssertEqual(report.days[0].sections.map(\.header), ["Breakfast 07:00–10:00"])
    }

    func test_makeReport_unpinnedEntryResolvesByTimeOfDay() throws {
        let food = makeFood(id: "lunch", day: 1, hour: 12)
        let report = makeSUT().makeReport(foods: [food], mealTypes: makeMealTypes(), from: try makeDate(day: 1), to: try makeDate(day: 1))

        XCTAssertEqual(report.days[0].sections.map(\.header), ["Lunch 11:00–14:00"])
    }

    func test_makeReport_entryOutsideEveryWindowGoesToTheTrailingUnassignedSection() throws {
        let foods = [makeFood(id: "night", day: 1, hour: 23), makeFood(id: "breakfast", day: 1, hour: 8)]
        let report = makeSUT().makeReport(foods: foods, mealTypes: makeMealTypes(), from: try makeDate(day: 1), to: try makeDate(day: 1))

        XCTAssertEqual(report.days[0].sections.last?.header, L10n.Dashboard.sectionUnassignedFoods)
        XCTAssertEqual(report.days[0].sections.count, 2)
    }

    func test_makeReport_ignoresEntriesOutsideTheInterval() throws {
        let foods = [makeFood(id: "in", day: 2, hour: 8), makeFood(id: "out", day: 5, hour: 8)]
        let report = makeSUT().makeReport(foods: foods, mealTypes: makeMealTypes(), from: try makeDate(day: 1), to: try makeDate(day: 3))

        XCTAssertEqual(report.days.flatMap(\.sections).flatMap(\.rows).count, 1)
    }

    func test_makeReport_unknownOptionalNutrientStaysUnknownOnTheRowButCountsAsZeroInTheTotal() throws {
        let unknown = makeFood(id: "unknown", day: 1, hour: 8, fiber: nil)
        let known = makeFood(id: "known", day: 1, hour: 8, fiber: 3)
        let report = makeSUT().makeReport(foods: [unknown, known], mealTypes: makeMealTypes(), from: try makeDate(day: 1), to: try makeDate(day: 1))

        let rows = try XCTUnwrap(report.days[0].sections.first?.rows)
        XCTAssertEqual(rows.filter { $0.fiber == nil }.count, 1)
        XCTAssertEqual(report.days[0].total?.fiber, 3)
    }

    // MARK: - Helpers

    private func makeSUT() -> FoodExportReportFactory {
        FoodExportReportFactory()
    }

    private func makeMealTypes() -> [MealTypeDomain] {
        [
            MealTypeDomain(id: "lunch", name: "Lunch", startTime: time(11), endTime: time(14)),
            MealTypeDomain(id: "breakfast", name: "Breakfast", startTime: time(7), endTime: time(10))
        ]
    }

    private func time(_ hour: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: 0, second: 0, of: .now) ?? .now
    }

    private func makeDate(day: Int, hour: Int = 0) throws -> Date {
        try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour)))
    }

    private func makeFood(id: String, day: Int, hour: Int, mealTypeId: String? = nil, fiber: Double? = 1) -> FoodConsumedDomain {
        let date = Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour)) ?? .now
        return FoodConsumedDomain(
            id: id,
            foodItemId: id,
            foodItemKind: .catalogue,
            czName: id,
            engName: id,
            weight: 100,
            date: date,
            calories: 100,
            caloriesPerHundredGrams: 100,
            energyKJ: 400,
            protein: 1,
            carbohydrate: 1,
            carbohydrateSugar: 1,
            fat: 1,
            fatSaturated: 1,
            fatUnsaturated: 1,
            fiber: fiber,
            salt: 1,
            mealTypeId: mealTypeId
        )
    }
}
