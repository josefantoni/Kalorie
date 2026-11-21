//
//  BilingualNamedTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 21.11.2026.
//

import XCTest
@testable import Kalorie

final class BilingualNamedTests: XCTestCase {

    // MARK: - Tests

    func test_displayName_onSlovakDevice_isTheSlovakName() {
        XCTAssertEqual(makeSUT().displayName(languageCode: "sk"), "Mlieko")
    }

    func test_displayName_onCzechDevice_isTheCzechName() {
        XCTAssertEqual(makeSUT().displayName(languageCode: "cs"), "Mléko")
    }

    func test_displayName_onEnglishDevice_isTheEnglishName() {
        XCTAssertEqual(makeSUT().displayName(languageCode: "en"), "Milk")
    }

    func test_displayName_onEnglishDeviceWithoutEnglishName_fallsBackToTheCzechName() {
        XCTAssertEqual(makeSUT(engName: "").displayName(languageCode: "en"), "Mléko")
    }

    func test_displayName_onCzechDeviceWithoutCzechName_fallsBackToTheSlovakNameBeforeEnglish() {
        XCTAssertEqual(makeSUT(czName: "").displayName(languageCode: "cs"), "Mlieko")
    }

    func test_displayName_onSlovakDeviceWithoutSlovakName_fallsBackToTheCzechNameBeforeEnglish() {
        XCTAssertEqual(makeSUT(skName: "").displayName(languageCode: "sk"), "Mléko")
    }

    func test_displayName_onEnglishDeviceWithOnlySlovakName_showsTheSlovakName() {
        XCTAssertEqual(makeSUT(czName: "", engName: "").displayName(languageCode: "en"), "Mlieko")
    }

    // MARK: - Helpers

    private func makeSUT(czName: String = "Mléko", skName: String = "Mlieko", engName: String = "Milk") -> NamedFake {
        NamedFake(czName: czName, skName: skName, engName: engName)
    }

    private struct NamedFake: BilingualNamed {
        let czName: String
        let skName: String
        let engName: String
    }
}
