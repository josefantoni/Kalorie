//
//  FoodPortionDomainTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 31.10.2026.
//

import XCTest
@testable import Kalorie

final class FoodPortionDomainTests: XCTestCase {

    // MARK: - Tests

    func test_displayName_usesTheCurrentLanguageCode() {
        let portion = FoodPortionDomain(name: "tbsp", grams: 15)

        XCTAssertEqual(portion.displayName, portion.displayName(languageCode: Locale.currentLanguageCode))
    }

    func test_displayName_inSlovak_showsTheSlovakPortionNameNotTheCzechOne() {
        let portion = FoodPortionDomain(name: "tbsp", grams: 15)

        XCTAssertEqual(portion.displayName(languageCode: "sk"), "1 lyžica")
    }

    func test_displayName_inCzech_showsTheCzechPortionName() {
        let portion = FoodPortionDomain(name: "tbsp", grams: 15)

        XCTAssertEqual(portion.displayName(languageCode: "cs"), "1 lžíce")
    }

    func test_displayName_inAnotherLanguage_showsTheEnglishPortionName() {
        let portion = FoodPortionDomain(name: "1 lžíce", grams: 15)

        XCTAssertEqual(portion.displayName(languageCode: "de"), "1 tbsp")
    }

    func test_displayName_withAnUnknownName_keepsTheNameCapitalised() {
        let portion = FoodPortionDomain(name: "hrnek", grams: 40)

        XCTAssertEqual(portion.displayName(languageCode: "en"), "Hrnek")
    }
}
