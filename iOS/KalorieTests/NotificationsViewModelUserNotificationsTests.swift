//
//  NotificationsViewModelUserNotificationsTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import FirebaseFirestore
import XCTest
@testable import Kalorie

final class NotificationsViewModelUserNotificationsTests: XCTestCase {

    // MARK: - Tests

    func test_onAppear_whenMaintainer_doesNotFetchUserNotifications() async {
        let spy = FetchUserNotificationsSpy()
        let sut = NotificationsViewModel(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(),
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            isMaintainer: true,
            fetchUserNotifications: spy,
            markUserNotificationSeen: MarkUserNotificationSeenUseCaseFake(),
            hideUserNotification: HideUserNotificationUseCaseFake(),
            withdrawUserNotification: WithdrawUserNotificationUseCaseFake(),
            notificationsStore: UserNotificationsStoreFake()
        )
        await sut.onAppear()
        XCTAssertEqual(spy.callCount, 0)
    }

    func test_onAppear_listsTheFetchedNotifications() async {
        let setup = makeSUT(notifications: [unread("111"), unread("222", kind: .photoSuggestion)])
        await setup.sut.onAppear()
        XCTAssertEqual(setup.sut.rows.map(\.id), ["foodItemReports/t111", "photoSuggestions/t222"])
    }

    func test_onAppear_withoutAResolvedName_fallsBackToTheItemId() async {
        let setup = makeSUT(notifications: [unread("111")])
        await setup.sut.onAppear()
        XCTAssertEqual(setup.sut.rows.first?.title, "111")
    }

    func test_onAppear_whenTheFetchFails_showsTheAlertAndKeepsTheStoreUntouched() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: true)
        let setup = makeSUT(shouldThrow: true, store: store)
        await setup.sut.onAppear()
        XCTAssertNotNil(setup.sut.alertItem)
        XCTAssertTrue(store.mayHaveUnread)
        XCTAssertNil(store.lastCheckedAt)
    }

    func test_onAppear_afterASuccessfulFetch_updatesTheStore() async {
        let store = UserNotificationsStoreFake(mayHaveUnread: false)
        let setup = makeSUT(notifications: [unread("111")], store: store)
        await setup.sut.onAppear()
        XCTAssertTrue(store.mayHaveUnread)
        XCTAssertNotNil(store.lastCheckedAt)
    }

    func test_onDetailShown_forAnUnreadRow_marksItOnceAndClearsItsDot() async {
        let setup = makeSUT(notifications: [unread("111"), unread("222")])
        await setup.sut.onAppear()

        await setup.sut.onDetailShown(id: "foodItemReports/t111")

        XCTAssertEqual(setup.mark.marked.map(\.itemId), ["111"])
        XCTAssertEqual(setup.sut.rows.map(\.isUnread), [false, true])
    }

    func test_onDetailShown_again_doesNotMarkASecondTimeWhileTheRowIsRead() async {
        let setup = makeSUT(notifications: [unread("111")])
        await setup.sut.onAppear()

        await setup.sut.onDetailShown(id: "foodItemReports/t111")
        await setup.sut.onDetailShown(id: "foodItemReports/t111")

        XCTAssertEqual(setup.mark.marked.count, 1, "once the row is read locally, another opening costs no write")
    }

    func test_onDetailShown_forAnAlreadyReadRow_marksNothing() async {
        let setup = makeSUT(notifications: [makeNotification(id: "t111", itemId: "111", createdAt: .now.addingTimeInterval(-60), seenAt: .now)])
        await setup.sut.onAppear()
        await setup.sut.onDetailShown(id: "foodItemReports/t111")
        XCTAssertTrue(setup.mark.marked.isEmpty)
    }

    func test_onDetailShown_whenMarkingFails_leavesTheRowUnreadWithoutAnAlert() async {
        let setup = makeSUT(notifications: [unread("111")], markFails: true)
        await setup.sut.onAppear()

        await setup.sut.onDetailShown(id: "foodItemReports/t111")

        XCTAssertEqual(setup.sut.rows.first?.isUnread, true, "the next opening must try again")
        XCTAssertNil(setup.sut.alertItem)
    }

    func test_onDetailShown_whenTheWriteFailsForAnotherFirestoreReason_leavesTheRowUnread() async {
        let store = UserNotificationsStoreFake()
        let unavailableError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.unavailable.rawValue)
        let setup = makeSUT(notifications: [unread("111")], markFails: true, markError: unavailableError, store: store)
        await setup.sut.onAppear()

        await setup.sut.onDetailShown(id: "foodItemReports/t111")

        XCTAssertEqual(setup.sut.rows.first?.isUnread, true)
        XCTAssertTrue(store.mayHaveUnread)
    }

    func test_onDetailShown_forTheLastUnreadRow_clearsMayHaveUnread() async {
        let store = UserNotificationsStoreFake()
        let setup = makeSUT(notifications: [unread("111"), unread("222")], store: store)
        await setup.sut.onAppear()
        XCTAssertTrue(store.mayHaveUnread)

        await setup.sut.onDetailShown(id: "foodItemReports/t111")
        XCTAssertTrue(store.mayHaveUnread)
        await setup.sut.onDetailShown(id: "foodItemReports/t222")

        XCTAssertFalse(store.mayHaveUnread, "otherwise the bell keeps querying until the daily check finds nothing unread")
    }

    func test_onDetailShown_whenMarkingFails_doesNotClearMayHaveUnread() async {
        let store = UserNotificationsStoreFake()
        let setup = makeSUT(notifications: [unread("111")], markFails: true, store: store)
        await setup.sut.onAppear()
        await setup.sut.onDetailShown(id: "foodItemReports/t111")
        XCTAssertTrue(store.mayHaveUnread)
    }

    func test_onWithdrawConfirmed_removesTheRowAndReportsWithdrawn() async {
        let setup = makeSUT(notifications: [unread("111"), unread("222")])
        await setup.sut.onAppear()

        let result = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t111")

        XCTAssertEqual(result, .withdrawn)
        XCTAssertEqual(setup.sut.rows.map(\.id), ["foodItemReports/t222"])
        XCTAssertEqual(setup.withdraw.withdrawn.map(\.itemId), ["111"])
    }

    func test_onWithdrawConfirmed_whenItWasTheLastUnreadRow_clearsMayHaveUnread() async {
        let store = UserNotificationsStoreFake()
        let setup = makeSUT(notifications: [unread("111")], store: store)
        await setup.sut.onAppear()
        XCTAssertTrue(store.mayHaveUnread)

        _ = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t111")

        XCTAssertFalse(store.mayHaveUnread, "a withdrawn notification can no longer be unread")
    }

    func test_onWithdrawConfirmed_whenAnotherRowIsStillUnread_keepsMayHaveUnread() async {
        let store = UserNotificationsStoreFake()
        let setup = makeSUT(notifications: [unread("111"), unread("222")], store: store)
        await setup.sut.onAppear()

        _ = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t111")

        XCTAssertTrue(store.mayHaveUnread)
    }

    func test_onWithdrawConfirmed_whenItFails_keepsTheRowAndReportsFailed() async {
        let store = UserNotificationsStoreFake()
        let setup = makeSUT(notifications: [unread("111")], withdrawFails: true, store: store)
        await setup.sut.onAppear()

        let result = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t111")

        XCTAssertEqual(result, .failed)
        XCTAssertEqual(setup.sut.rows.map(\.id), ["foodItemReports/t111"], "the row stays so the user can retry")
        XCTAssertTrue(store.mayHaveUnread)
    }

    func test_onWithdrawConfirmed_whenTheDeleteFailsForAnotherFirestoreReason_keepsTheRowAndReportsFailed() async {
        let store = UserNotificationsStoreFake()
        let unavailableError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.unavailable.rawValue)
        let setup = makeSUT(notifications: [unread("111")], withdrawFails: true, withdrawError: unavailableError, store: store)
        await setup.sut.onAppear()

        let result = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t111")

        XCTAssertEqual(result, .failed)
        XCTAssertEqual(setup.sut.rows.map(\.id), ["foodItemReports/t111"])
        XCTAssertTrue(store.mayHaveUnread)
    }

    func test_onWithdrawConfirmed_forAnUnknownRow_reportsFailed() async {
        let setup = makeSUT(notifications: [unread("111")])
        await setup.sut.onAppear()

        let result = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t999")

        XCTAssertEqual(result, .failed)
        XCTAssertTrue(setup.withdraw.withdrawn.isEmpty)
    }

    func test_onWithdrawConfirmed_whenTheMaintainerResolvedItMeanwhile_reloadsTheListAndReportsAlreadyResolved() async {
        let setup = makeSUT(
            notifications: [unread("111")],
            withdrawFails: true,
            withdrawError: WithdrawUserNotificationError.alreadyResolved
        )
        await setup.sut.onAppear()

        let result = await setup.sut.onWithdrawConfirmed(id: "foodItemReports/t111")

        XCTAssertEqual(result, .alreadyResolved)
        XCTAssertEqual(setup.fetch.callCount, 2, "the user must see the outcome the maintainer wrote, not a stale pending row")
    }

    func test_onHideTapped_removesTheRowAtOnceAndHidesTheTicket() async {
        let setup = makeSUT(notifications: [unread("111"), unread("222")])
        await setup.sut.onAppear()

        await setup.sut.onHideTapped(id: "foodItemReports/t111")

        XCTAssertEqual(setup.sut.rows.map(\.id), ["foodItemReports/t222"])
        XCTAssertEqual(setup.hide.hidden.map(\.id), ["t111"])
    }

    func test_onHideTapped_whenItFails_restoresTheRowInPlaceAndAlerts() async {
        let setup = makeSUT(notifications: [unread("111"), unread("222")], hideFails: true)
        await setup.sut.onAppear()

        await setup.sut.onHideTapped(id: "foodItemReports/t111")

        XCTAssertEqual(setup.sut.rows.map(\.id), ["foodItemReports/t111", "foodItemReports/t222"], "a row that was not hidden server-side must not disappear")
        XCTAssertEqual(setup.sut.alertItem?.title, L10n.Common.errorUnknown)
    }

    func test_onHideTapped_whenTheLastUnreadRowIsHidden_clearsMayHaveUnread() async {
        let store = UserNotificationsStoreFake()
        let setup = makeSUT(notifications: [unread("111")], store: store)
        await setup.sut.onAppear()
        XCTAssertTrue(store.mayHaveUnread)

        await setup.sut.onHideTapped(id: "foodItemReports/t111")

        XCTAssertFalse(store.mayHaveUnread, "a hidden notification can no longer light the bell")
    }

    // MARK: - Helpers

    private func unread(_ itemId: String, kind: UserNotificationDomain.Kind = .report) -> UserNotificationDomain {
        makeNotification(kind: kind, id: "t\(itemId)", itemId: itemId, createdAt: .now)
    }

    private func makeSUT(
        notifications: [UserNotificationDomain] = [],
        shouldThrow: Bool = false,
        markFails: Bool = false,
        markError: Error = URLError(.unknown),
        withdrawFails: Bool = false,
        withdrawError: Error = URLError(.unknown),
        hideFails: Bool = false,
        store: UserNotificationsStoreFake = UserNotificationsStoreFake()
    ) -> (sut: NotificationsViewModel, mark: MarkSeenRecordingFake, withdraw: WithdrawRecordingFake, hide: HideRecordingFake, fetch: FetchUserNotificationsCounter) {
        let mark = MarkSeenRecordingFake(shouldThrow: markFails, error: markError)
        let withdraw = WithdrawRecordingFake(shouldThrow: withdrawFails, error: withdrawError)
        let hide = HideRecordingFake(shouldThrow: hideFails)
        let fetch = FetchUserNotificationsCounter(notifications: notifications, shouldThrow: shouldThrow)
        let sut = NotificationsViewModel(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(),
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            isMaintainer: false,
            fetchUserNotifications: fetch,
            markUserNotificationSeen: mark,
            hideUserNotification: hide,
            withdrawUserNotification: withdraw,
            notificationsStore: store
        )
        addTeardownBlock { [weak sut] in
            XCTAssertNil(sut, "Potential memory leak")
        }
        return (sut, mark, withdraw, hide, fetch)
    }
}

private final class WithdrawRecordingFake: WithdrawUserNotificationUseCaseProtocol {

    // MARK: - Properties

    let shouldThrow: Bool
    let error: Error
    private(set) var withdrawn: [UserNotificationDomain] = []

    // MARK: - Init

    init(shouldThrow: Bool, error: Error) {
        self.shouldThrow = shouldThrow
        self.error = error
    }

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        if shouldThrow { throw error }
        withdrawn.append(notification)
    }
}

private final class MarkSeenRecordingFake: MarkUserNotificationSeenUseCaseProtocol {

    // MARK: - Properties

    let shouldThrow: Bool
    let error: Error
    private(set) var marked: [UserNotificationDomain] = []

    // MARK: - Init

    init(shouldThrow: Bool, error: Error) {
        self.shouldThrow = shouldThrow
        self.error = error
    }

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        if shouldThrow { throw error }
        marked.append(notification)
    }
}

private final class HideRecordingFake: HideUserNotificationUseCaseProtocol {

    // MARK: - Properties

    let shouldThrow: Bool
    private(set) var hidden: [UserNotificationDomain] = []

    // MARK: - Init

    init(shouldThrow: Bool) {
        self.shouldThrow = shouldThrow
    }

    // MARK: - Functions

    func callAsFunction(_ notification: UserNotificationDomain) async throws {
        if shouldThrow { throw URLError(.unknown) }
        hidden.append(notification)
    }
}

private final class FetchUserNotificationsCounter: FetchUserNotificationsUseCaseProtocol {

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

private final class FetchUserNotificationsSpy: FetchUserNotificationsUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0

    // MARK: - Functions

    func callAsFunction() async throws -> [UserNotificationDomain] {
        callCount += 1
        return []
    }
}
