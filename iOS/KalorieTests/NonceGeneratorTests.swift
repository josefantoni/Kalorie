//
//  NonceGeneratorTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
@testable import Kalorie

final class NonceGeneratorTests: XCTestCase {

    // MARK: - randomNonceString

    func test_randomNonceString_hasRequestedLength() {
        XCTAssertEqual(NonceGenerator.randomNonceString(length: 32).count, 32)
        XCTAssertEqual(NonceGenerator.randomNonceString(length: 10).count, 10)
    }

    func test_randomNonceString_usesOnlyAllowedCharset() {
        let allowed = Set("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        let nonce = NonceGenerator.randomNonceString(length: 128)
        XCTAssertTrue(nonce.allSatisfy { allowed.contains($0) })
    }

    func test_randomNonceString_generatesDifferentValuesEachCall() {
        let first = NonceGenerator.randomNonceString()
        let second = NonceGenerator.randomNonceString()
        XCTAssertNotEqual(first, second, "nonce musí být pokaždé jiný, jinak by šlo o replay útok")
    }

    // MARK: - sha256

    func test_sha256_ofEmptyString_matchesKnownVector() {
        XCTAssertEqual(
            NonceGenerator.sha256(""),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
    }

    func test_sha256_isDeterministic() {
        XCTAssertEqual(NonceGenerator.sha256("kalorie"), NonceGenerator.sha256("kalorie"))
    }

    func test_sha256_differentInputsProduceDifferentHashes() {
        XCTAssertNotEqual(NonceGenerator.sha256("a"), NonceGenerator.sha256("b"))
    }
}
