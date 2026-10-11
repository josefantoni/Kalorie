//
//  UIKitDynamicTypeLimitTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 11.10.2026.
//

import XCTest
@testable import Kalorie

final class UIKitDynamicTypeLimitTests: XCTestCase {

    func test_overrideCategory_systemAboveMaximum_capsToMaximum() {
        XCTAssertEqual(UIKitDynamicTypeLimit.overrideCategory(forSystem: .accessibilityExtraExtraExtraLarge), UIKitDynamicTypeLimit.maximum)
        XCTAssertEqual(UIKitDynamicTypeLimit.overrideCategory(forSystem: .extraExtraLarge), UIKitDynamicTypeLimit.maximum)
    }

    func test_overrideCategory_systemAtOrBelowMaximum_doesNotOverride() {
        XCTAssertNil(UIKitDynamicTypeLimit.overrideCategory(forSystem: UIKitDynamicTypeLimit.maximum))
        XCTAssertNil(UIKitDynamicTypeLimit.overrideCategory(forSystem: .large))
        XCTAssertNil(UIKitDynamicTypeLimit.overrideCategory(forSystem: .extraSmall))
    }
}
