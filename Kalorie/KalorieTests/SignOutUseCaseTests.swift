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
        let (sut, authCommandProvider, _, _) = makeSUT()
        try sut()
        XCTAssertEqual(authCommandProvider.signOutCallCount, 1)
    }

    func test_callAsFunction_whenSignOutFails_throwsError() {
        let (sut, authCommandProvider, _, _) = makeSUT()
        authCommandProvider.signOutError = URLError(.unknown)

        XCTAssertThrowsError(try sut())
    }

    func test_callAsFunction_discardsPendingMergeSnapshot() throws {
        let (sut, _, snapshotStore, _) = makeSUT()
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [], favouriteFoods: [])

        try sut()

        XCTAssertNil(snapshotStore.stubbedSnapshot)
    }

    func test_callAsFunction_discardsSnapshotBeforeSigningOut() {
        let (sut, authCommandProvider, snapshotStore, _) = makeSUT()
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [], favouriteFoods: [])
        authCommandProvider.signOutError = URLError(.unknown)

        XCTAssertThrowsError(try sut())
        XCTAssertNil(snapshotStore.stubbedSnapshot)
    }

    func test_callAsFunction_clearsGoogleSession() throws {
        let (sut, _, _, googleSessionProvider) = makeSUT()

        try sut()

        XCTAssertEqual(googleSessionProvider.clearSessionCallCount, 1)
    }

    func test_callAsFunction_whenSignOutFails_doesNotClearGoogleSession() {
        let (sut, authCommandProvider, _, googleSessionProvider) = makeSUT()
        authCommandProvider.signOutError = URLError(.unknown)

        XCTAssertThrowsError(try sut())
        XCTAssertEqual(googleSessionProvider.clearSessionCallCount, 0)
    }

    // MARK: - Helpers

    private func makeSUT() -> (
        sut: SignOutUseCase,
        authCommandProvider: AuthCommandProviderFake,
        snapshotStore: PendingMergeSnapshotStoreFake,
        googleSessionProvider: GoogleSessionProviderFake
    ) {
        let authCommandProvider = AuthCommandProviderFake()
        let snapshotStore = PendingMergeSnapshotStoreFake()
        let googleSessionProvider = GoogleSessionProviderFake()
        let sut = SignOutUseCase(
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore,
            googleSessionProvider: googleSessionProvider
        )
        return (sut, authCommandProvider, snapshotStore, googleSessionProvider)
    }
}
