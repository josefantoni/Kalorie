//
//  NotificationBellViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class NotificationBellViewModelTests: XCTestCase {

    // MARK: - Tests

    func test_onAppear_whenMaintainer_showsTheBellAndTheDot() async {
        let (sut, _) = makeSUT(claim: .success(true), hasPending: true)

        await sut.onAppear()

        XCTAssertTrue(sut.isBellVisible)
        XCTAssertTrue(sut.hasPendingNotifications)
    }

    func test_onForeground_afterAFailedClaimLookup_showsTheBell() async {
        let (sut, claim) = makeSUT(claim: .failure(AuthError.notAuthenticated), hasPending: true)
        await sut.onAppear()
        XCTAssertFalse(sut.isBellVisible)

        claim.result = .success(true)
        await sut.onForeground()

        XCTAssertTrue(sut.isBellVisible, "one failed lookup must not hide the bell for the rest of the session")
        XCTAssertTrue(sut.hasPendingNotifications)
    }

    func test_onSheetDismissed_recomputesTheDot() async {
        let (sut, _, pending) = makeSUTWithPending(claim: .success(true), hasPending: true)
        await sut.onAppear()
        XCTAssertTrue(sut.hasPendingNotifications)

        pending.result = false
        await sut.onSheetDismissed()

        XCTAssertFalse(sut.hasPendingNotifications)
    }

    // MARK: - Helpers

    private func makeSUT(claim: Result<Bool, Error>, hasPending: Bool) -> (sut: NotificationBellViewModel, claim: FetchMaintainerClaimFake) {
        let (sut, claimFake, _) = makeSUTWithPending(claim: claim, hasPending: hasPending)
        return (sut, claimFake)
    }

    private func makeSUTWithPending(
        claim: Result<Bool, Error>,
        hasPending: Bool
    ) -> (sut: NotificationBellViewModel, claim: FetchMaintainerClaimFake, pending: FetchHasPendingFake) {
        let claimFake = FetchMaintainerClaimFake(result: claim)
        let pendingFake = FetchHasPendingFake(result: hasPending)
        let sut = NotificationBellViewModel(fetchMaintainerClaim: claimFake, fetchHasPendingNotifications: pendingFake)
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }
        return (sut, claimFake, pendingFake)
    }
}

private final class FetchMaintainerClaimFake: FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Properties

    var result: Result<Bool, Error>

    // MARK: - Init

    init(result: Result<Bool, Error>) {
        self.result = result
    }

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        try result.get()
    }
}

private final class FetchHasPendingFake: FetchHasPendingNotificationsUseCaseProtocol {

    // MARK: - Properties

    var result: Bool

    // MARK: - Init

    init(result: Bool) {
        self.result = result
    }

    // MARK: - Functions

    func callAsFunction() async -> Bool {
        result
    }
}
