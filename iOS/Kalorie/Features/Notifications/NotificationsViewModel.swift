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
            case report(barcode: String, reports: [FoodItemReportDomain])
            case photoSuggestion(PhotoSuggestionDomain)
        }

        let id: String
        let kind: Kind
        let title: String
        let date: Date
    }

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var rows: [Row] = []
    @Published var alertItem: AlertItem?

    private let fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol
    private let fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol
    private let fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol

    // MARK: - Init

    init(
        fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol,
        fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol,
        fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) {
        self.fetchPendingSubmissions = fetchPendingSubmissions
        self.fetchFoodItemReports = fetchFoodItemReports
        self.fetchPhotoSuggestions = fetchPhotoSuggestions
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
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
    func onPhotoSuggestionResolved(id: String, isApproved: Bool) {
        var resolvedItemId: String?
        if case .photoSuggestion(let suggestion)? = rows.first(where: { $0.id == id })?.kind {
            resolvedItemId = suggestion.itemId
        }
        rows.removeAll { row in
            guard case .photoSuggestion(let suggestion) = row.kind else { return false }
            return row.id == id || (isApproved && suggestion.itemId == resolvedItemId)
        }
    }

    @MainActor
    func onReportRowChanged(id: String, remaining: [FoodItemReportDomain]) {
        guard let index = rows.firstIndex(where: { $0.id == id }) else { return }
        guard
            !remaining.isEmpty,
            case .report(let barcode, _) = rows[index].kind
        else {
            rows.remove(at: index)
            return
        }
        let row = rows[index]
        rows[index] = Row(id: row.id, kind: .report(barcode: barcode, reports: remaining), title: row.title, date: row.date)
    }

    // MARK: - Private

    @MainActor
    private func load() async {
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
        let reportRows = Dictionary(grouping: reports, by: \.barcode).map { barcode, reports in
            Row(
                id: "report-\(barcode)",
                kind: .report(barcode: barcode, reports: reports.sorted { $0.reportedAt > $1.reportedAt }),
                title: namesById[barcode] ?? barcode,
                date: reports.map(\.reportedAt).min() ?? .distantPast
            )
        }
        let suggestionRows = suggestions.map {
            Row(id: "photo-\($0.id)", kind: .photoSuggestion($0), title: namesById[$0.itemId] ?? $0.itemId, date: $0.suggestedAt)
        }
        return (submissionRows + reportRows + suggestionRows).sorted { $0.date < $1.date }
    }
}
