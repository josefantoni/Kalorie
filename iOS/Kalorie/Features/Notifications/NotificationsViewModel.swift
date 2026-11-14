//
//  NotificationsViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

final class NotificationsViewModel: ObservableObject {

    // MARK: - Types

    struct Row: Identifiable {
        enum Kind {
            case submission(FoodItemSubmissionDomain)
            case report(FoodItemReportDomain)
            case photoSuggestion(PhotoSuggestionDomain)
            case userNotification(UserNotificationDomain)
        }

        let id: String
        var kind: Kind
        let title: String
        let date: Date

        var isHideable: Bool {
            guard case .userNotification = kind else { return false }
            return true
        }

        var isUnread: Bool {
            guard case .userNotification(let notification) = kind else { return false }
            return notification.isUnread
        }
    }

    enum WithdrawResult {
        case withdrawn
        case alreadyResolved
        case failed
    }

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var rows: [Row] = []
    @Published var alertItem: AlertItem?

    private let fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol
    private let fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol
    private let fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let isMaintainer: Bool
    private let fetchUserNotifications: any FetchUserNotificationsUseCaseProtocol
    private let markUserNotificationSeen: any MarkUserNotificationSeenUseCaseProtocol
    private let hideUserNotification: any HideUserNotificationUseCaseProtocol
    private let withdrawUserNotification: any WithdrawUserNotificationUseCaseProtocol
    private let notificationsStore: any UserNotificationsStoreProtocol

    // MARK: - Init

    init(
        fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol,
        fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol,
        fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        isMaintainer: Bool,
        fetchUserNotifications: any FetchUserNotificationsUseCaseProtocol,
        markUserNotificationSeen: any MarkUserNotificationSeenUseCaseProtocol,
        hideUserNotification: any HideUserNotificationUseCaseProtocol,
        withdrawUserNotification: any WithdrawUserNotificationUseCaseProtocol,
        notificationsStore: any UserNotificationsStoreProtocol
    ) {
        self.fetchPendingSubmissions = fetchPendingSubmissions
        self.fetchFoodItemReports = fetchFoodItemReports
        self.fetchPhotoSuggestions = fetchPhotoSuggestions
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.isMaintainer = isMaintainer
        self.fetchUserNotifications = fetchUserNotifications
        self.markUserNotificationSeen = markUserNotificationSeen
        self.hideUserNotification = hideUserNotification
        self.withdrawUserNotification = withdrawUserNotification
        self.notificationsStore = notificationsStore
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        guard case .idle = state else { return }
        state = .loading
        defer { state = .loaded }
        await load()
    }

    @MainActor
    func onRefresh() async {
        await load()
    }

    @MainActor
    func onRowResolved(id: String) {
        rows.removeAll { $0.id == id }
    }

    @MainActor
    func onDetailShown(id: String) async {
        guard
            let index = rows.firstIndex(where: { $0.id == id }),
            case .userNotification(let notification) = rows[index].kind,
            notification.isUnread
        else { return }
        do {
            try await markUserNotificationSeen(notification)
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
            return
        }
        markSeenLocally(id: id)
    }

    @MainActor
    func onWithdrawConfirmed(id: String) async -> WithdrawResult {
        guard
            let row = rows.first(where: { $0.id == id }),
            case .userNotification(let notification) = row.kind
        else { return .failed }
        do {
            try await withdrawUserNotification(notification)
        } catch WithdrawUserNotificationError.alreadyResolved {
            await load()
            return .alreadyResolved
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            return .failed
        }
        rows.removeAll { $0.id == id }
        notificationsStore.mayHaveUnread = rows.contains(where: \.isUnread)
        return .withdrawn
    }

    @MainActor
    func onHideTapped(id: String) async {
        guard
            let index = rows.firstIndex(where: { $0.id == id }),
            case .userNotification(let notification) = rows[index].kind
        else { return }
        let row = rows.remove(at: index)
        do {
            try await hideUserNotification(notification)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            rows.insert(row, at: min(index, rows.count))
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
            return
        }
        notificationsStore.mayHaveUnread = rows.contains(where: \.isUnread)
    }

    // MARK: - Private

    @MainActor
    private func markSeenLocally(id: String) {
        guard
            let index = rows.firstIndex(where: { $0.id == id }),
            case .userNotification(var seen) = rows[index].kind
        else { return }
        seen.seenAt = .now
        rows[index].kind = .userNotification(seen)
        if !rows.contains(where: \.isUnread) {
            notificationsStore.mayHaveUnread = false
        }
    }

    @MainActor
    private func load() async {
        if isMaintainer {
            await loadModeration()
        } else {
            await loadUserNotifications()
        }
    }

    @MainActor
    private func loadUserNotifications() async {
        let notifications: [UserNotificationDomain]
        let startedAt = Date.now
        do {
            notifications = try await fetchUserNotifications()
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
            return
        }
        notificationsStore.recordFetch(notifications, startedAt: startedAt)
        let items = (try? await fetchFoodItemByBarcode(barcodes: Array(Set(notifications.map(\.itemId))))) ?? []
        let namesById = Dictionary(items.map { ($0.id, $0.displayName) }) { first, _ in first }
        rows = notifications.map {
            Row(id: $0.rowId, kind: .userNotification($0), title: namesById[$0.itemId] ?? $0.itemId, date: $0.lastEventAt)
        }
    }

    @MainActor
    private func loadModeration() async {
        async let submissionsResult = Self.attempt { try await fetchPendingSubmissions() }
        async let reportsResult = Self.attempt { try await fetchFoodItemReports() }
        async let suggestionsResult = Self.attempt { try await fetchPhotoSuggestions() }
        let (fetchedSubmissions, fetchedReports, fetchedSuggestions) = await (submissionsResult, reportsResult, suggestionsResult)

        if
            fetchedSubmissions == nil
            || fetchedReports == nil
            || fetchedSuggestions == nil
        {
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
        let submissions = fetchedSubmissions ?? []
        let reports = fetchedReports ?? []
        let suggestions = fetchedSuggestions ?? []

        let barcodes = Set(reports.map(\.barcode)).union(suggestions.map(\.itemId))
        let items = (try? await fetchFoodItemByBarcode(barcodes: Array(barcodes))) ?? []
        let namesById = Dictionary(items.map { ($0.id, $0.displayName) }) { first, _ in first }
        let keptRows = rows.filter { row in
            switch row.kind {
            case .submission: fetchedSubmissions == nil
            case .report: fetchedReports == nil
            case .photoSuggestion: fetchedSuggestions == nil
            case .userNotification: false
            }
        }
        rows = (Self.rows(submissions: submissions, reports: reports, suggestions: suggestions, namesById: namesById) + keptRows)
            .sorted { $0.date < $1.date }
    }

    private static func attempt<T>(_ operation: () async throws -> T) async -> T? {
        do {
            return try await operation()
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            return nil
        }
    }

    private static func rows(
        submissions: [FoodItemSubmissionDomain],
        reports: [FoodItemReportDomain],
        suggestions: [PhotoSuggestionDomain],
        namesById: [String: String]
    ) -> [Row] {
        let submissionRows = submissions.map {
            Row(id: "submission-\($0.id)", kind: .submission($0), title: $0.item.displayName, date: $0.submittedAt)
        }
        let reportRows = reports.map {
            Row(id: "report-\($0.id)", kind: .report($0), title: namesById[$0.barcode] ?? $0.barcode, date: $0.reportedAt)
        }
        let suggestionRows = suggestions.map {
            Row(id: "photo-\($0.id)", kind: .photoSuggestion($0), title: namesById[$0.itemId] ?? $0.itemId, date: $0.suggestedAt)
        }
        return (submissionRows + reportRows + suggestionRows).sorted { $0.date < $1.date }
    }
}
