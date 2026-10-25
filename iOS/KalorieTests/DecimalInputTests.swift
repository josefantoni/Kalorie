//
//  DecimalInputTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 26.10.2026.
//

import XCTest
@testable import Kalorie

final class DecimalInputTests: XCTestCase {

    // MARK: - Tests

    func test_sanitizedDecimalInput_matchesSharedFixtureCases() throws {
        let fixture: DecimalInputFixture = try FixtureLoader.load("text-kit-cases", directory: "TextKit/fixtures")

        XCTAssertFalse(fixture.decimalInputSanitize.isEmpty)
        for sanitizeCase in fixture.decimalInputSanitize {
            XCTAssertEqual(sanitizeCase.input.sanitizedDecimalInput(), sanitizeCase.expected, sanitizeCase.input)
        }
    }

    func test_decimalInputValue_matchesSharedFixtureCases() throws {
        let fixture: DecimalInputFixture = try FixtureLoader.load("text-kit-cases", directory: "TextKit/fixtures")

        XCTAssertFalse(fixture.decimalInputParse.isEmpty)
        for parseCase in fixture.decimalInputParse {
            XCTAssertEqual(parseCase.input.decimalInputValue, parseCase.expected, parseCase.input)
        }
    }
}

private struct DecimalInputFixture: Decodable {
    struct SanitizeCase: Decodable {
        let input: String
        let expected: String
    }

    struct ParseCase: Decodable {
        let input: String
        let expected: Double?
    }

    let decimalInputSanitize: [SanitizeCase]
    let decimalInputParse: [ParseCase]
}
