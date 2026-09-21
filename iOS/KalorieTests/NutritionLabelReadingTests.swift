//
//  NutritionLabelReadingTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.09.2026.
//

import XCTest
@testable import Kalorie

final class NutritionLabelReadingTests: XCTestCase {

    // MARK: - isCompleteForAutoCapture

    func test_isCompleteForAutoCapture_withKcalFatCarbsProtein_isTrue() {
        let reading = NutritionLabelReading(caloriesPerHundredGrams: 370, fat: 12, carbohydrate: 55, protein: 10)
        XCTAssertTrue(reading.isCompleteForAutoCapture, "a live read with the four macros the predicate needs must fire capture, or the shutter is the only way in")
    }

    func test_isCompleteForAutoCapture_missingOneMacro_isFalse() {
        let reading = NutritionLabelReading(caloriesPerHundredGrams: 370, fat: 12, carbohydrate: 55, protein: nil)
        XCTAssertFalse(reading.isCompleteForAutoCapture, "capturing on a partial read wastes the photo on a table the camera has not finished reading")
    }

    func test_isCompleteForAutoCapture_barcodeOnly_isFalse() {
        let reading = NutritionLabelReading(scannedCode: "12345678")
        XCTAssertFalse(reading.isCompleteForAutoCapture, "a barcode alone is not a nutrition table and must not trigger a capture")
    }
}
