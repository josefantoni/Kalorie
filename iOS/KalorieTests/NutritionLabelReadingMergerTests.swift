//
//  NutritionLabelReadingMergerTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 03.10.2026.
//

import XCTest
@testable import Kalorie

final class NutritionLabelReadingMergerTests: XCTestCase {

    // MARK: - Tests

    func test_merge_valueSeenInTwoFrames_winsOverOneMisreadFrame() {
        let readings = [
            NutritionLabelReading(fat: 0.1, carbohydrate: 15),
            NutritionLabelReading(fat: 0.1, carbohydrate: 150),
            NutritionLabelReading(carbohydrate: 15)
        ]

        let merged = NutritionLabelReadingMerger.merge(readings)

        XCTAssertEqual(merged.carbohydrate, 15, "a decimal comma dropped in one frame must be outvoted by the frames that read it")
        XCTAssertEqual(merged.fat, 0.1)
    }

    func test_merge_valueSeenOnce_isDroppedBecauseNothingConfirmsIt() {
        let readings = [
            NutritionLabelReading(fiber: 14),
            NutritionLabelReading(fiber: 1.4),
            NutritionLabelReading()
        ]

        XCTAssertNil(NutritionLabelReadingMerger.merge(readings).fiber, "a value no second frame confirms is as likely a misread as not, and an empty field is cheaper than a wrong one")
    }

    func test_merge_framesDisagreeEverywhere_isEmpty() {
        let readings = [
            NutritionLabelReading(protein: 0.5),
            NutritionLabelReading(protein: 5),
            NutritionLabelReading(protein: 50)
        ]

        XCTAssertTrue(NutritionLabelReadingMerger.merge(readings).isEmpty)
    }

    func test_merge_noFrames_isEmpty() {
        XCTAssertTrue(NutritionLabelReadingMerger.merge([]).isEmpty)
    }
}
