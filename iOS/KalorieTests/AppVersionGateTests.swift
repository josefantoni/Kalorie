//
//  AppVersionGateTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 21.10.2026.
//

import XCTest
@testable import Kalorie

@MainActor
final class AppVersionGateTests: XCTestCase {

    // MARK: - Tests

    func test_check_whenCurrentIsBelowMinimum_requiresUpdate() async {
        let sut = makeSUT(current: "1.0", minimum: "1.0.1")
        await sut.check()
        XCTAssertTrue(sut.isUpdateRequired)
    }

    func test_check_whenCurrentEqualsMinimum_doesNotRequireUpdate() async {
        let sut = makeSUT(current: "1.0.1", minimum: "1.0.1")
        await sut.check()
        XCTAssertFalse(sut.isUpdateRequired)
    }

    func test_check_whenNoMinimumIsSet_doesNotRequireUpdate() async {
        let sut = makeSUT(current: "1.0", minimum: nil)
        await sut.check()
        XCTAssertFalse(sut.isUpdateRequired)
    }

    func test_check_whenCurrentVersionDoesNotParse_doesNotRequireUpdate() async {
        let sut = makeSUT(current: nil, minimum: "9.0")
        await sut.check()
        XCTAssertFalse(sut.isUpdateRequired)
    }

    func test_check_whenReadFails_doesNotRequireUpdate() async {
        let sut = makeSUT(current: "1.0", minimum: "9.0", shouldThrow: true)
        await sut.check()
        XCTAssertFalse(sut.isUpdateRequired)
    }

    func test_check_whenReadFailsAfterBlocking_keepsBlocking() async {
        let fetch = ToggleableFetchMinimumVersionUseCase(minimum: AppVersion("2.0"))
        let sut = AppVersionGate(fetchMinimumVersion: fetch, currentVersion: AppVersion("1.0"))
        await sut.check()
        fetch.shouldThrow = true
        await sut.check()
        XCTAssertTrue(sut.isUpdateRequired)
    }

    func test_check_whenMinimumIsLoweredAfterBlocking_unblocks() async {
        let fetch = ToggleableFetchMinimumVersionUseCase(minimum: AppVersion("2.0"))
        let sut = AppVersionGate(fetchMinimumVersion: fetch, currentVersion: AppVersion("1.0"))
        await sut.check()
        fetch.minimum = nil
        await sut.check()
        XCTAssertFalse(sut.isUpdateRequired)
    }

    // MARK: - Helpers

    private func makeSUT(current: String?, minimum: String?, shouldThrow: Bool = false) -> AppVersionGate {
        AppVersionGate(
            fetchMinimumVersion: FetchMinimumVersionUseCaseFake(
                stubbedMinimum: minimum.flatMap(AppVersion.init),
                shouldThrow: shouldThrow
            ),
            currentVersion: current.flatMap(AppVersion.init)
        )
    }
}

private final class ToggleableFetchMinimumVersionUseCase: FetchMinimumVersionUseCaseProtocol {

    // MARK: - Properties

    var minimum: AppVersion?
    var shouldThrow = false

    // MARK: - Init

    init(minimum: AppVersion?) {
        self.minimum = minimum
    }

    // MARK: - Functions

    func callAsFunction() async throws -> AppVersion? {
        if shouldThrow { throw URLError(.unknown) }
        return minimum
    }
}
