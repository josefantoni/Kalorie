//
//  AppConfigDTOTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 21.10.2026.
//

import XCTest
@testable import Kalorie

final class AppConfigDTOTests: XCTestCase {

    // MARK: - Tests

    func test_decode_readsBothStringFields() throws {
        let dto = try decode(["min_version_ios": "1.0.1", "min_version_android": "2.0"])
        XCTAssertEqual(dto.minVersionIos, "1.0.1")
        XCTAssertEqual(dto.minVersionAndroid, "2.0")
    }

    func test_decode_whenFieldHasWrongType_decodesAsNilInsteadOfThrowing() throws {
        let dto = try decode(["min_version_ios": 1.5, "min_version_android": "2.0"])
        XCTAssertNil(dto.minVersionIos, "a mistyped console value must switch the gate off, not leave a blocked client blocked")
        XCTAssertEqual(dto.minVersionAndroid, "2.0", "one bad field must not hide the other")
    }

    func test_decode_whenFieldsAreAbsent_decodesAsNil() throws {
        let dto = try decode([:])
        XCTAssertNil(dto.minVersionIos)
        XCTAssertNil(dto.minVersionAndroid)
    }

    // MARK: - Helpers

    private func decode(_ json: [String: Any]) throws -> AppConfigDTO {
        try JSONDecoder().decode(AppConfigDTO.self, from: JSONSerialization.data(withJSONObject: json))
    }
}
