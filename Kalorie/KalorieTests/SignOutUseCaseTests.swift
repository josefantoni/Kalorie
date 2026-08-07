//
//  SignOutUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
@testable import Kalorie

final class SignOutUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_callsSignOutOnAuthCommandProvider() throws {
        let (sut, authCommandProvider, _) = makeSUT()
        try sut()
        XCTAssertEqual(authCommandProvider.signOutCallCount, 1)
    }

    func test_callAsFunction_whenSignOutFails_throwsError() {
        let (sut, authCommandProvider, _) = makeSUT()
        authCommandProvider.signOutError = URLError(.unknown)

        XCTAssertThrowsError(try sut())
    }

    func test_callAsFunction_discardsPendingMergeSnapshot() throws {
        let (sut, _, snapshotStore) = makeSUT()
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [])

        try sut()

        XCTAssertNil(snapshotStore.stubbedSnapshot)
    }

    func test_callAsFunction_discardsSnapshotBeforeSigningOut() {
        let (sut, authCommandProvider, snapshotStore) = makeSUT()
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [])
        authCommandProvider.signOutError = URLError(.unknown)

        XCTAssertThrowsError(try sut())
        XCTAssertNil(snapshotStore.stubbedSnapshot)
    }

    // MARK: - Helpers

    private func makeSUT() -> (
        sut: SignOutUseCase,
        authCommandProvider: AuthCommandProviderFake,
        snapshotStore: PendingMergeSnapshotStoreFake
    ) {
        let authCommandProvider = AuthCommandProviderFake()
        let snapshotStore = PendingMergeSnapshotStoreFake()
        let sut = SignOutUseCase(authCommandProvider: authCommandProvider, snapshotStore: snapshotStore)
        return (sut, authCommandProvider, snapshotStore)
    }
}
