//
//  MaintainerClaimCacheTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 11.09.2026.
//

import XCTest
@testable import Kalorie

final class MaintainerClaimCacheTests: XCTestCase {

    // MARK: - Tests

    func test_value_whenNothingStoredYet_isNil() {
        let sut = MaintainerClaimCache()
        XCTAssertNil(sut.value)
    }

    func test_value_afterStoring_returnsStoredValue() {
        let sut = MaintainerClaimCache()
        sut.value = true
        XCTAssertEqual(sut.value, true)
    }

    func test_value_isSharedAcrossHoldersOfTheSameInstance() {
        let sut = MaintainerClaimCache()
        let otherHolder = sut
        sut.value = true
        XCTAssertEqual(otherHolder.value, true, "the cache must be a reference type so every use case built from the same configurator sees the same value")
    }
}
