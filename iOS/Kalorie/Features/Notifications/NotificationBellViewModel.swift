//
//  NotificationBellViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

final class NotificationBellViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var isBellVisible = false
    @Published private(set) var hasPendingNotifications = false
    @Published var isSheetVisible = false

    private let fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol
    private let fetchHasPendingNotifications: any FetchHasPendingNotificationsUseCaseProtocol

    // MARK: - Init

    init(
        fetchMaintainerClaim: any FetchMaintainerClaimUseCaseProtocol,
        fetchHasPendingNotifications: any FetchHasPendingNotificationsUseCaseProtocol
    ) {
        self.fetchMaintainerClaim = fetchMaintainerClaim
        self.fetchHasPendingNotifications = fetchHasPendingNotifications
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
    func onBellTapped() {
        isSheetVisible = true
    }

    @MainActor
    func onSheetDismissed() async {
        await refreshDot()
    }

    // MARK: - Private

    @MainActor
    private func refresh() async {
        do {
            isBellVisible = try await fetchMaintainerClaim()
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
            isBellVisible = false
        }
        await refreshDot()
    }

    @MainActor
    private func refreshDot() async {
        guard isBellVisible else {
            hasPendingNotifications = false
            return
        }
        hasPendingNotifications = await fetchHasPendingNotifications()
    }
}
