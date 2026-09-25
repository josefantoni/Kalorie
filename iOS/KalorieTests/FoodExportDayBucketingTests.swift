//
//  FoodExportDayBucketingTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 25.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodExportDayBucketingTests: XCTestCase {

    // MARK: - Tests

    func test_makeReport_matchesSharedDayBucketingFixtureCases() throws {
        let fixture: BucketingFixture = try FixtureLoader.load("export-day-bucketing-cases")
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: fixture.timeZone))
        let sut = FoodExportReportFactory(calendar: calendar)

        for bucketingCase in fixture.cases {
            let foods = try bucketingCase.entries.enumerated().map { index, entry in
                makeFood(name: "entry-\(index)", date: try parse(entry))
            }
            let report = sut.makeReport(
                foods: foods,
                mealTypes: [],
                from: try parse(bucketingCase.from),
                to: try parse(bucketingCase.to)
            )

            XCTAssertEqual(report.days.count, bucketingCase.expectedDayCount, bucketingCase.name)
            let actualIndexes: [Int?] = foods.indices.map { index in
                report.days.firstIndex { day in
                    day.sections.contains { section in section.rows.contains { $0.name == "entry-\(index)" } }
                }
            }
            XCTAssertEqual(actualIndexes, bucketingCase.expectedDayIndexes, bucketingCase.name)
        }
    }

    // MARK: - Helpers

    private func parse(_ text: String) throws -> Date {
        try XCTUnwrap(ISO8601DateFormatter().date(from: text), text)
    }

    private func makeFood(name: String, date: Date) -> FoodConsumedDomain {
        FoodConsumedDomain(
            id: name,
            foodItemId: name,
            foodItemKind: .catalogue,
            czName: name,
            engName: name,
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
            fiber: 1,
            salt: 1,
            mealTypeId: nil
        )
    }
}

private struct BucketingFixture: Decodable {
    let timeZone: String
    let cases: [BucketingCase]
}

private struct BucketingCase: Decodable {
    let name: String
    let from: String
    let to: String
    let entries: [String]
    let expectedDayCount: Int
    let expectedDayIndexes: [Int?]
}
