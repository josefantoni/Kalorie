//
//  NutritionLabelParserTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 13.09.2026.
//

import XCTest
@testable import Kalorie

final class NutritionLabelParserTests: XCTestCase {

    func test_parse_matchesSharedFixtureCases() throws {
        let fixture: ParsingFixture = try FixtureLoader.load("nutrition-label-parsing-cases")
        for parsingCase in fixture.cases {
            let lines = parsingCase.lines.map {
                RecognizedTextLine(text: $0.text, boundingBox: CGRect(x: $0.xPosition, y: $0.yPosition, width: $0.width, height: $0.height))
            }
            let reading = NutritionLabelParser.parse(lines: lines)
            let expected = parsingCase.expected
            let name = parsingCase.name

            XCTAssertEqual(reading.measure, expected.measure, name)
            assertEqual(reading.energyKJ, expected.energyKJ, name)
            assertEqual(reading.caloriesPerHundredGrams, expected.caloriesPerHundredGrams, name)
            assertEqual(reading.fat, expected.fat, name)
            assertEqual(reading.fatSaturated, expected.fatSaturated, name)
            assertEqual(reading.fatUnsaturatedFattyAcids, expected.fatUnsaturatedFattyAcids, name)
            assertEqual(reading.carbohydrate, expected.carbohydrate, name)
            assertEqual(reading.carbohydratePureSugar, expected.carbohydratePureSugar, name)
            assertEqual(reading.fiber, expected.fiber, name)
            assertEqual(reading.protein, expected.protein, name)
            assertEqual(reading.salt, expected.salt, name)
        }
    }

    func test_merging_fillsNameAndPortionsTheParserCouldNotFind() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())
        let candidate = NutritionLabelModelCandidate(
            name: "Tvaroh",
            portions: [NutritionLabelPortionCandidate(name: "1 balení", grams: 250)]
        )
        let ocrText = (czechLabelLines().map(\.text) + ["Tvaroh", "1 balení 250 g"]).joined(separator: "\n")

        let merged = NutritionLabelParser.merging(reading, with: candidate, ocrText: ocrText)

        XCTAssertEqual(merged.name, "Tvaroh")
        XCTAssertEqual(merged.portions?.first?.grams, 250)
    }

    func test_merging_neverOverwritesAValueTheParserAlreadyFound() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())
        let candidate = NutritionLabelModelCandidate(fatPer100g: 999)
        let ocrText = czechLabelLines().map(\.text).joined(separator: "\n") + "\n999"

        let merged = NutritionLabelParser.merging(reading, with: candidate, ocrText: ocrText)

        XCTAssertEqual(merged.fat, 12, "the parser's own deterministic value must win over the model's")
    }

    func test_merging_discardsAModelNumberThatDoesNotAppearInTheOCRText() {
        let reading = NutritionLabelReading()
        let candidate = NutritionLabelModelCandidate(fatPer100g: 250)
        let merged = NutritionLabelParser.merging(reading, with: candidate, ocrText: "no numbers here at all")

        XCTAssertNil(merged.fat, "an ungrounded number must never be written into the form")
    }

    // MARK: - Helpers

    private func assertEqual(_ actual: Double?, _ expected: Double?, _ name: String) {
        switch (actual, expected) {
        case (nil, nil):
            break
        case (let actual?, let expected?):
            XCTAssertEqual(actual, expected, accuracy: 1e-9, name)
        default:
            XCTFail("\(name): expected \(String(describing: expected)), got \(String(describing: actual))")
        }
    }

    private func czechLabelLines() -> [RecognizedTextLine] {
        [
            RecognizedTextLine(text: "Nutriční hodnoty na 100 g", boundingBox: CGRect(x: 0.6, y: 0.9, width: 0.3, height: 0.03)),
            RecognizedTextLine(text: "Energetická hodnota", boundingBox: CGRect(x: 0.1, y: 0.8, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "1550 kJ / 370 kcal", boundingBox: energyValueBox),
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.1, y: 0.7, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.7, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "z toho nasycené mastné kyseliny", boundingBox: CGRect(x: 0.1, y: 0.65, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "2 g", boundingBox: saturatesValueBox),
            RecognizedTextLine(text: "Sacharidy", boundingBox: CGRect(x: 0.1, y: 0.55, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "55 g", boundingBox: carbohydrateValueBox),
            RecognizedTextLine(text: "z toho cukry", boundingBox: CGRect(x: 0.1, y: 0.5, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "5 g", boundingBox: CGRect(x: 0.6, y: 0.5, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Bílkoviny", boundingBox: CGRect(x: 0.1, y: 0.4, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "10 g", boundingBox: CGRect(x: 0.6, y: 0.4, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Sůl", boundingBox: CGRect(x: 0.1, y: 0.3, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "1 g", boundingBox: CGRect(x: 0.6, y: 0.3, width: 0.15, height: 0.05))
        ]
    }

    private let saturatesValueBox = CGRect(x: 0.6, y: 0.65, width: 0.15, height: 0.05)
    private let carbohydrateValueBox = CGRect(x: 0.6, y: 0.55, width: 0.15, height: 0.05)
    private let energyValueBox = CGRect(x: 0.6, y: 0.8, width: 0.3, height: 0.05)

    private struct ParsingFixture: Decodable {
        let cases: [ParsingCase]
    }

    private struct ParsingCase: Decodable {
        let name: String
        let lines: [FixtureLine]
        let expected: ExpectedReading
    }

    private struct FixtureLine: Decodable {
        let text: String
        let xPosition: Double
        let yPosition: Double
        let width: Double
        let height: Double

        enum CodingKeys: String, CodingKey {
            case text, width, height
            case xPosition = "x"
            case yPosition = "y"
        }
    }

    private struct ExpectedReading: Decodable {
        let energyKJ: Double?
        let caloriesPerHundredGrams: Double?
        let fat: Double?
        let fatSaturated: Double?
        let fatUnsaturatedFattyAcids: Double?
        let carbohydrate: Double?
        let carbohydratePureSugar: Double?
        let fiber: Double?
        let protein: Double?
        let salt: Double?
        let measure: FoodMeasure?
    }
}
