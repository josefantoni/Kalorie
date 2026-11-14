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

    func test_onAppear_whenMaintainer_usesThePendingCountsForTheDot() async {
        let (sut, _) = makeSUT(claim: .success(true), hasPending: true)

        await sut.onAppear()

        XCTAssertTrue(sut.isMaintainer)
        XCTAssertTrue(sut.hasPendingNotifications)
    }

    func test_onForeground_afterAFailedClaimLookup_recognisesTheMaintainerAgain() async {
        let (sut, claim) = makeSUT(claim: .failure(AuthError.notAuthenticated), hasPending: true)
        await sut.onAppear()
        XCTAssertFalse(sut.isMaintainer)

        claim.result = .success(true)
        await sut.onForeground()

        XCTAssertTrue(sut.isMaintainer, "one failed lookup must not demote the maintainer for the rest of the session")
        XCTAssertTrue(sut.hasPendingNotifications)
    }

    func test_onBellTapped_beforeTheMaintainerClaimResolves_doesNotOpenTheSheet() async {
        let claim = SuspendedClaimFake()
        let sut = makeSUT(claimFake: claim)

        let appear = Task { await sut.onAppear() }
        await claim.waitUntilRequested()
        await sut.onBellTapped()
        XCTAssertFalse(sut.isSheetVisible, "a maintainer tapping during the claim lookup would get the user list for the whole sheet")
        claim.resolve(.success(true))
        await appear.value
        await sut.onBellTapped()

        XCTAssertTrue(sut.isSheetVisible)
        XCTAssertTrue(sut.isMaintainer)
    }

    func test_onBellTapped_afterAFailedClaimLookup_opensTheSheetForTheUser() async {
        let (sut, _) = makeSUT(claim: .failure(AuthError.notAuthenticated), hasPending: false)
        await sut.onAppear()

        await sut.onBellTapped()

        XCTAssertTrue(sut.isSheetVisible)
        XCTAssertFalse(sut.isMaintainer)
    }

    func test_onSheetDismissed_recomputesTheDot() async {
        let (sut, _, pending) = makeSUTWithPending(claim: .success(true), hasPending: true)
        await sut.onAppear()
        XCTAssertTrue(sut.hasPendingNotifications)

        pending.result = false
        await sut.onSheetDismissed()

        XCTAssertFalse(sut.hasPendingNotifications)
    }

    func test_onAppear_whenMaintainer_neverCallsTheUserFetch() async {
        let setup = makeFullSUT(claim: .success(true), hasPending: true)
        await setup.sut.onAppear()
        XCTAssertEqual(setup.user.callCount, 0)
    }

    func test_onAppear_whenClaimLookupFails_treatsTheUserAsNonMaintainer() async {
        let setup = makeFullSUT(claim: .failure(AuthError.notAuthenticated), hasPending: true, unread: [unread()])
        await setup.sut.onAppear()
        XCTAssertFalse(setup.sut.isMaintainer)
        XCTAssertTrue(setup.sut.hasPendingNotifications)
        XCTAssertEqual(setup.user.callCount, 1)
    }

    func test_onAppear_nonMaintainerWithAnUnreadNotification_showsTheDot() async {
        let setup = makeFullSUT(unread: [unread()], store: UserNotificationsStoreFake(mayHaveUnread: true))
        await setup.sut.onAppear()
        XCTAssertTrue(setup.sut.hasPendingNotifications)
    }

    func test_onAppear_nonMaintainerWithEverythingRead_showsNoDot() async {
        let setup = makeFullSUT(unread: [read()])
        await setup.sut.onAppear()
        XCTAssertFalse(setup.sut.hasPendingNotifications)
    }

    func test_onAppear_whenTheUserFetchFails_showsNoDot() async {
        let setup = makeFullSUT(userFetchFails: true)
        await setup.sut.onAppear()
        XCTAssertFalse(setup.sut.hasPendingNotifications)
    }

    func test_onAppear_whenNothingMayBeUnreadAndTheLastCheckIsFresh_doesNotQuery() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: .now.addingTimeInterval(-3_600))
        let setup = makeFullSUT(unread: [unread()], store: store)
        await setup.sut.onAppear()
        XCTAssertEqual(setup.user.callCount, 0, "two reads on every Dashboard appearance for every user is the cost the gate exists to avoid")
        XCTAssertFalse(setup.sut.hasPendingNotifications)
    }

    func test_onAppear_whenAnUnreadNotificationIsPossible_queries() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: true, lastCheckedAt: .now)
        let setup = makeFullSUT(unread: [unread()], store: store)
        await setup.sut.onAppear()
        XCTAssertEqual(setup.user.callCount, 1)
    }

    func test_onAppear_withNoPreviousCheck_queries() async {
        let setup = makeFullSUT(store: UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: nil))
        await setup.sut.onAppear()
        XCTAssertEqual(setup.user.callCount, 1)
    }

    func test_onAppear_whenTheLastCheckIsOlderThanADay_queries() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: .now.addingTimeInterval(-25 * 3_600))
        let setup = makeFullSUT(store: store)
        await setup.sut.onAppear()
        XCTAssertEqual(setup.user.callCount, 1, "this daily check is how a report sent on another device reaches this one")
    }

    func test_onAppear_afterASuccessfulFetchWithNothingUnread_clearsTheFlagAndStampsTheCheck() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: true, lastCheckedAt: nil)
        let setup = makeFullSUT(unread: [read()], store: store)
        await setup.sut.onAppear()
        XCTAssertFalse(store.mayHaveUnread)
        XCTAssertNotNil(store.lastCheckedAt)
    }

    func test_onAppear_afterAFetchWithAnUnreadNotification_keepsTheFlag() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: nil)
        let setup = makeFullSUT(unread: [unread()], store: store)
        await setup.sut.onAppear()
        XCTAssertTrue(store.mayHaveUnread)
        XCTAssertNotNil(store.lastCheckedAt)
    }

    func test_onAppear_afterAFailedFetch_changesNeitherTheFlagNorTheTimestamp() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: true, lastCheckedAt: nil)
        let setup = makeFullSUT(userFetchFails: true, store: store)
        await setup.sut.onAppear()
        XCTAssertTrue(store.mayHaveUnread)
        XCTAssertNil(store.lastCheckedAt)
    }

    func test_onRefresh_queriesEvenWhenTheGateIsClosed() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: .now)
        let setup = makeFullSUT(unread: [unread()], store: store)
        await setup.sut.onRefresh()
        XCTAssertEqual(setup.user.callCount, 1, "pull to refresh is the user's way to pull in a notification from another device")
        XCTAssertTrue(setup.sut.hasPendingNotifications)
    }

    func test_onDotRefreshNeeded_respectsTheGate() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: .now)
        let setup = makeFullSUT(unread: [unread()], store: store)
        await setup.sut.onAppear()
        await setup.sut.onDotRefreshNeeded()
        XCTAssertEqual(setup.user.callCount, 0)
    }

    func test_onAppearAndOnForeground_whileARefreshIsInFlight_queryTheUserNotificationsOnlyOnce() async {
        let userFake = SuspendedFirstUserFetchFake()
        let sut = makeSUT(userFetch: userFake, store: UserNotificationsStoreFake(mayHaveUnread: true))

        let appear = Task { await sut.onAppear() }
        await userFake.waitUntilRequested()
        let foreground = Task { await sut.onForeground() }
        await foreground.value
        userFake.release()
        await appear.value

        XCTAssertEqual(userFake.callCount, 1, "DashboardView fires both on entry; the second must not repeat the billed queries")
    }

    func test_onForeground_whenTheLastCheckIsInTheFuture_queriesAndRestampsTheCheck() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: .now.addingTimeInterval(3 * 24 * 3_600))
        let setup = makeFullSUT(store: store)

        await setup.sut.onForeground()

        XCTAssertEqual(setup.user.callCount, 1, "a clock that was ahead during a fetch must not keep the daily check closed")
        let lastCheckedAt = store.lastCheckedAt ?? .distantFuture
        XCTAssertLessThan(abs(lastCheckedAt.timeIntervalSinceNow), 60)
    }

    func test_onAppear_whenASubmitLandsDuringTheFetch_keepsTheFlagAndDoesNotStampTheCheck() async {
        let userFake = SuspendedFirstUserFetchFake()
        let store = UserNotificationsStoreFake(mayHaveUnread: false, lastCheckedAt: nil)
        let sut = makeSUT(userFetch: userFake, store: store)

        let appear = Task { await sut.onAppear() }
        await userFake.waitUntilRequested()
        store.mayHaveUnread = true
        store.lastSubmittedAt = .now
        userFake.release()
        await appear.value

        XCTAssertTrue(store.mayHaveUnread, "the fetch started before the submit and cannot have seen the new document")
        XCTAssertNil(store.lastCheckedAt, "stamping the check would close the gate for a day")
        XCTAssertTrue(sut.hasPendingNotifications)
    }

    func test_recordFetch_whenSubmittedAfterTheFetchStarted_changesNothing() {
        let store = UserNotificationsStoreFake(mayHaveUnread: true, lastCheckedAt: nil, lastSubmittedAt: .now)

        store.recordFetch([], startedAt: .now.addingTimeInterval(-1))

        XCTAssertTrue(store.mayHaveUnread)
        XCTAssertNil(store.lastCheckedAt)
    }

    func test_recordFetch_whenSubmittedBeforeTheFetchStarted_recordsTheResult() {
        let store = UserNotificationsStoreFake(mayHaveUnread: true, lastCheckedAt: nil, lastSubmittedAt: .now.addingTimeInterval(-10))

        store.recordFetch([], startedAt: .now)

        XCTAssertFalse(store.mayHaveUnread)
        XCTAssertNotNil(store.lastCheckedAt)
    }

    func test_onRefresh_whenAGatedRefreshFinishesFirst_stillGuardsTheDotAgainstTheNextForeground() async {
        let userFake = SuspendedFirstTwoUserFetchesFake()
        let sut = makeSUT(userFetch: userFake, store: UserNotificationsStoreFake(mayHaveUnread: true))

        let appear = Task { await sut.onAppear() }
        await userFake.waitUntilRequested()
        let refresh = Task { await sut.onRefresh() }
        await userFake.waitUntilRequested()
        userFake.releaseOldest()
        await appear.value
        await sut.onSheetDismissed()

        XCTAssertEqual(userFake.callCount, 2, "the pull's fetch is still in flight, so the dismissal must not start a third billed query")

        userFake.releaseOldest()
        await refresh.value
    }

    func test_onSheetDismissed_whileARefreshIsInFlight_recomputesTheMaintainersDotAfterwards() async {
        let pendingFake = SuspendedFirstPendingFake()
        let sut = NotificationBellViewModel(
            fetchMaintainerClaim: FetchMaintainerClaimFake(result: .success(true)),
            fetchHasPendingNotifications: pendingFake,
            fetchUserNotifications: FetchUserNotificationsUseCaseFake(),
            notificationsStore: UserNotificationsStoreFake()
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }

        let foreground = Task { await sut.onForeground() }
        await pendingFake.waitUntilRequested()
        await sut.onSheetDismissed()
        pendingFake.release()
        await foreground.value

        XCTAssertFalse(sut.hasPendingNotifications, "the count taken before the queue was resolved must not leave the dot on")
        XCTAssertEqual(pendingFake.callCount, 2)
    }

    func test_recordFetch_whenSubmittedInTheFuture_recordsTheResult() {
        let store = UserNotificationsStoreFake(mayHaveUnread: true, lastCheckedAt: nil, lastSubmittedAt: .now.addingTimeInterval(3_600))

        store.recordFetch([], startedAt: .now)

        XCTAssertFalse(store.mayHaveUnread, "a clock set back after a submit must not drop every later fetch")
        XCTAssertNotNil(store.lastCheckedAt)
    }

    func test_onDotRefreshNeededAndOnSheetDismissed_beforeTheClaimResolves_doNotTakeTheUserBranch() async {
        let claim = SuspendedClaimFake()
        let userFake = FetchUserNotificationsCountingFake(notifications: [unread()], shouldThrow: false)
        let store = UserNotificationsStoreFake(mayHaveUnread: true)
        let sut = NotificationBellViewModel(
            fetchMaintainerClaim: claim,
            fetchHasPendingNotifications: FetchHasPendingFake(result: true),
            fetchUserNotifications: userFake,
            notificationsStore: store
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }

        let appear = Task { await sut.onAppear() }
        await claim.waitUntilRequested()
        await sut.onDotRefreshNeeded()
        await sut.onSheetDismissed()

        XCTAssertEqual(userFake.callCount, 0, "a maintainer must never run the user queries")
        XCTAssertNil(store.lastCheckedAt)

        claim.resolve(.success(true))
        await appear.value

        XCTAssertTrue(sut.isMaintainer)
        XCTAssertTrue(sut.hasPendingNotifications)
        XCTAssertEqual(userFake.callCount, 0)
    }

    // MARK: - Helpers

    private func makeSUT(
        userFetch: any FetchUserNotificationsUseCaseProtocol,
        store: UserNotificationsStoreFake
    ) -> NotificationBellViewModel {
        let sut = NotificationBellViewModel(
            fetchMaintainerClaim: FetchMaintainerClaimFake(result: .success(false)),
            fetchHasPendingNotifications: FetchHasPendingFake(result: false),
            fetchUserNotifications: userFetch,
            notificationsStore: store
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }
        return sut
    }

    private func unread() -> UserNotificationDomain {
        makeNotification(itemId: "111", createdAt: .now.addingTimeInterval(-60))
    }

    private func read() -> UserNotificationDomain {
        makeNotification(itemId: "111", createdAt: .now.addingTimeInterval(-60), seenAt: .now)
    }

    private func makeFullSUT(
        claim: Result<Bool, Error> = .success(false),
        hasPending: Bool = false,
        unread: [UserNotificationDomain] = [],
        userFetchFails: Bool = false,
        store: UserNotificationsStoreFake = UserNotificationsStoreFake()
    ) -> (sut: NotificationBellViewModel, user: FetchUserNotificationsCountingFake) {
        let userFake = FetchUserNotificationsCountingFake(notifications: unread, shouldThrow: userFetchFails)
        let sut = NotificationBellViewModel(
            fetchMaintainerClaim: FetchMaintainerClaimFake(result: claim),
            fetchHasPendingNotifications: FetchHasPendingFake(result: hasPending),
            fetchUserNotifications: userFake,
            notificationsStore: store
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }
        return (sut, userFake)
    }

    private func makeSUT(claimFake: any FetchMaintainerClaimUseCaseProtocol) -> NotificationBellViewModel {
        let sut = NotificationBellViewModel(
            fetchMaintainerClaim: claimFake,
            fetchHasPendingNotifications: FetchHasPendingFake(result: true),
            fetchUserNotifications: FetchUserNotificationsUseCaseFake(),
            notificationsStore: UserNotificationsStoreFake()
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }
        return sut
    }

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
        let sut = NotificationBellViewModel(
            fetchMaintainerClaim: claimFake,
            fetchHasPendingNotifications: pendingFake,
            fetchUserNotifications: FetchUserNotificationsUseCaseFake(),
            notificationsStore: UserNotificationsStoreFake()
        )
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

private final class SuspendedClaimFake: FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Properties

    private let requests: AsyncStream<Void>
    private let requestsContinuation: AsyncStream<Void>.Continuation
    private var pending: CheckedContinuation<Bool, Error>?

    // MARK: - Init

    init() {
        (requests, requestsContinuation) = AsyncStream.makeStream(of: Void.self)
    }

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        try await withCheckedThrowingContinuation { continuation in
            pending = continuation
            requestsContinuation.yield()
        }
    }

    func waitUntilRequested() async {
        for await _ in requests { return }
    }

    func resolve(_ result: Result<Bool, Error>) {
        pending?.resume(with: result)
        pending = nil
    }
}

private final class SuspendedFirstUserFetchFake: FetchUserNotificationsUseCaseProtocol {

    // MARK: - Properties

    private let requests: AsyncStream<Void>
    private let requestsContinuation: AsyncStream<Void>.Continuation
    private var pending: CheckedContinuation<Void, Never>?
    private(set) var callCount = 0

    // MARK: - Init

    init() {
        (requests, requestsContinuation) = AsyncStream.makeStream(of: Void.self)
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [UserNotificationDomain] {
        callCount += 1
        if callCount == 1 {
            await withCheckedContinuation { continuation in
                pending = continuation
                requestsContinuation.yield()
            }
        }
        return []
    }

    func waitUntilRequested() async {
        for await _ in requests { return }
    }

    func release() {
        pending?.resume()
        pending = nil
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

private final class FetchUserNotificationsCountingFake: FetchUserNotificationsUseCaseProtocol {

    // MARK: - Properties

    let notifications: [UserNotificationDomain]
    let shouldThrow: Bool
    private(set) var callCount = 0

    // MARK: - Init

    init(notifications: [UserNotificationDomain], shouldThrow: Bool) {
        self.notifications = notifications
        self.shouldThrow = shouldThrow
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [UserNotificationDomain] {
        callCount += 1
        if shouldThrow { throw URLError(.unknown) }
        return notifications
    }
}

private final class SuspendedFirstTwoUserFetchesFake: FetchUserNotificationsUseCaseProtocol {

    // MARK: - Properties

    private let requests: AsyncStream<Void>
    private let requestsContinuation: AsyncStream<Void>.Continuation
    private var pending: [CheckedContinuation<Void, Never>] = []
    private(set) var callCount = 0

    // MARK: - Init

    init() {
        (requests, requestsContinuation) = AsyncStream.makeStream(of: Void.self)
    }

    // MARK: - Functions

    func callAsFunction() async throws -> [UserNotificationDomain] {
        callCount += 1
        if callCount <= 2 {
            await withCheckedContinuation { continuation in
                pending.append(continuation)
                requestsContinuation.yield()
            }
        }
        return [makeNotification(itemId: "111", createdAt: .now.addingTimeInterval(-60))]
    }

    func waitUntilRequested() async {
        for await _ in requests { return }
    }

    func releaseOldest() {
        guard !pending.isEmpty else { return }
        pending.removeFirst().resume()
    }
}

private final class SuspendedFirstPendingFake: FetchHasPendingNotificationsUseCaseProtocol {

    // MARK: - Properties

    private let requests: AsyncStream<Void>
    private let requestsContinuation: AsyncStream<Void>.Continuation
    private var pending: CheckedContinuation<Void, Never>?
    private(set) var callCount = 0

    // MARK: - Init

    init() {
        (requests, requestsContinuation) = AsyncStream.makeStream(of: Void.self)
    }

    // MARK: - Functions

    func callAsFunction() async -> Bool {
        callCount += 1
        guard callCount == 1 else { return false }
        await withCheckedContinuation { continuation in
            pending = continuation
            requestsContinuation.yield()
        }
        return true
    }

    func waitUntilRequested() async {
        for await _ in requests { return }
    }

    func release() {
        pending?.resume()
        pending = nil
    }
}
