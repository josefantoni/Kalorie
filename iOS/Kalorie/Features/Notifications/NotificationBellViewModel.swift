//
//  NotificationBellViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

final class NotificationBellViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var isMaintainer = false
    @Published private(set) var hasPendingNotifications = false
    @Published var isSheetVisible = false

    private var isMaintainerResolved = false
    private var refreshCount = 0
    private var needsDotRecompute = false
    private let fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol
    private let fetchHasPendingNotifications: any FetchHasPendingNotificationsUseCaseProtocol
    private let fetchUserNotifications: any FetchUserNotificationsUseCaseProtocol
    private let notificationsStore: any UserNotificationsStoreProtocol

    // MARK: - Init

    init(
        fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol,
        fetchHasPendingNotifications: any FetchHasPendingNotificationsUseCaseProtocol,
        fetchUserNotifications: any FetchUserNotificationsUseCaseProtocol,
        notificationsStore: any UserNotificationsStoreProtocol
    ) {
        self.fetchMaintainerClaim = fetchMaintainerClaim
        self.fetchHasPendingNotifications = fetchHasPendingNotifications
        self.fetchUserNotifications = fetchUserNotifications
        self.notificationsStore = notificationsStore
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        await refresh()
    }

    @MainActor
    func onForeground() async {
        await refresh()
    }

    @MainActor
    func onRefresh() async {
        await refresh(ignoresGate: true)
    }

    @MainActor
    func onDotRefreshNeeded() async {
        guard isMaintainerResolved, refreshCount == 0 else { return }
        await refreshDot(ignoresGate: false)
    }

    @MainActor
    func onBellTapped() {
        guard isMaintainerResolved else { return }
        isSheetVisible = true
    }

    @MainActor
    func onSheetDismissed() async {
        guard isMaintainerResolved else { return }
        if refreshCount > 0 {
            needsDotRecompute = true
            return
        }
        await refreshDot()
    }

    // MARK: - Private

    @MainActor
    private func refresh(ignoresGate: Bool = false) async {
        if refreshCount > 0 && !ignoresGate { return }
        refreshCount += 1
        defer { refreshCount -= 1 }
        do {
            isMaintainer = try await fetchMaintainerClaim()
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
            isMaintainer = false
        }
        isMaintainerResolved = true
        await refreshDot(ignoresGate: ignoresGate)
        if needsDotRecompute {
            needsDotRecompute = false
            await refreshDot(ignoresGate: ignoresGate)
        }
    }

    @MainActor
    private func refreshDot(ignoresGate: Bool = false) async {
        if isMaintainer {
            hasPendingNotifications = await fetchHasPendingNotifications()
            return
        }
        guard ignoresGate || isCheckDue else {
            hasPendingNotifications = false
            return
        }
        let startedAt = Date.now
        do {
            let notifications = try await fetchUserNotifications()
            notificationsStore.recordFetch(notifications, startedAt: startedAt)
            hasPendingNotifications = notificationsStore.mayHaveUnread
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
            hasPendingNotifications = false
        }
    }

    private var isCheckDue: Bool {
        guard !notificationsStore.mayHaveUnread else { return true }
        guard let lastCheckedAt = notificationsStore.lastCheckedAt else { return true }
        let elapsed = Date.now.timeIntervalSince(lastCheckedAt)
        return elapsed < 0 || elapsed > Constants.Notifications.userCheckInterval
    }
}
