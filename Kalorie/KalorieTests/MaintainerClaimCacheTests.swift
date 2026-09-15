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

    func test_entry_whenNothingStoredYet_isNil() {
        let sut = MaintainerClaimCache()
        XCTAssertNil(sut.entry)
    }

    func test_entry_afterStoring_returnsStoredValue() {
        let sut = MaintainerClaimCache()
        sut.entry = .init(value: true, cachedAt: .now)
        XCTAssertEqual(sut.entry?.value, true)
    }

    func test_entry_isSharedAcrossHoldersOfTheSameInstance() {
        let sut = MaintainerClaimCache()
        let otherHolder = sut
        sut.entry = .init(value: true, cachedAt: .now)
        XCTAssertEqual(otherHolder.entry?.value, true, "the cache must be a reference type so every use case built from the same configurator sees the same value")
    }
}
