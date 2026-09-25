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
        sut.entry = .init(value: true, cachedAt: .now, userId: "user-1")
        XCTAssertEqual(sut.entry?.value, true)
    }

    func test_entry_isSharedAcrossHoldersOfTheSameInstance() {
        let sut = MaintainerClaimCache()
        let otherHolder = sut
        sut.entry = .init(value: true, cachedAt: .now, userId: "user-1")
        XCTAssertEqual(otherHolder.entry?.value, true, "the cache must be a reference type so every use case built from the same configurator sees the same value")
    }

    func test_freshValue_forTheSameUserWithinTheTimeToLive_returnsTheStoredValue() {
        let now = Date.now
        let sut = MaintainerClaimCache()
        sut.entry = .init(value: true, cachedAt: now.addingTimeInterval(-10), userId: "user-1")
        XCTAssertEqual(sut.freshValue(userId: "user-1", now: now, timeToLive: 300), true)
    }

    func test_freshValue_afterTheTimeToLive_returnsNil() {
        let now = Date.now
        let sut = MaintainerClaimCache()
        sut.entry = .init(value: true, cachedAt: now.addingTimeInterval(-301), userId: "user-1")
        XCTAssertNil(sut.freshValue(userId: "user-1", now: now, timeToLive: 300))
    }

    func test_freshValue_forADifferentUser_returnsNilSoTheClaimIsNeverServedToAnotherAccount() {
        let now = Date.now
        let sut = MaintainerClaimCache()
        sut.entry = .init(value: true, cachedAt: now.addingTimeInterval(-10), userId: "maintainer")
        XCTAssertNil(sut.freshValue(userId: "someone-else", now: now, timeToLive: 300))
    }
}
