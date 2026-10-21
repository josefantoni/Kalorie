//
//  AppVersionTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 21.10.2026.
//

import XCTest
@testable import Kalorie

final class AppVersionTests: XCTestCase {

    // MARK: - Tests

    func test_init_matchesSharedParseFixtureCases() throws {
        let fixture: VersionFixture = try FixtureLoader.load("app-version-comparison")

        for parseCase in fixture.parse {
            XCTAssertEqual(AppVersion(parseCase.version) != nil, parseCase.valid, parseCase.name)
        }
    }

    func test_compare_matchesSharedComparisonFixtureCases() throws {
        let fixture: VersionFixture = try FixtureLoader.load("app-version-comparison")

        for comparisonCase in fixture.compare {
            let lhs = try XCTUnwrap(AppVersion(comparisonCase.lhs), comparisonCase.name)
            let rhs = try XCTUnwrap(AppVersion(comparisonCase.rhs), comparisonCase.name)
            XCTAssertEqual(lhs < rhs, comparisonCase.expected < 0, comparisonCase.name)
            XCTAssertEqual(lhs > rhs, comparisonCase.expected > 0, comparisonCase.name)
            XCTAssertEqual(lhs == rhs, comparisonCase.expected == 0, comparisonCase.name)
        }
    }

    func test_bundleMarketingVersion_parses() throws {
        let version = try XCTUnwrap(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String)
        XCTAssertNotNil(AppVersion(version), "The gate is silently off when the real version name does not parse")
    }
}

private struct VersionFixture: Decodable {
    struct ParseCase: Decodable {
        let name: String
        let version: String
        let valid: Bool
    }

    struct ComparisonCase: Decodable {
        let name: String
        let lhs: String
        let rhs: String
        let expected: Int
    }

    let parse: [ParseCase]
    let compare: [ComparisonCase]
}
