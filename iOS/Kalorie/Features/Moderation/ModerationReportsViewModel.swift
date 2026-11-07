//
//  ModerationReportsViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

final class ModerationReportsViewModel: ObservableObject {

    // MARK: - Types

    struct ReportGroup: Identifiable {
        let barcode: String
        let itemName: String?
        let reports: [FoodItemReportDomain]

        var id: String { barcode }
    }

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var groups: [ReportGroup] = []
    @Published var alertItem: AlertItem?

    private let fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol

    // MARK: - Init

    init(
        fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) {
        self.fetchFoodItemReports = fetchFoodItemReports
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
    func onGroupReportsChanged(barcode: String, remaining: [FoodItemReportDomain]) {
        guard let index = groups.firstIndex(where: { $0.barcode == barcode }) else { return }
        guard !remaining.isEmpty else {
            groups.remove(at: index)
            return
        }
        let group = groups[index]
        groups[index] = ReportGroup(barcode: barcode, itemName: group.itemName, reports: remaining)
    }

    // MARK: - Private

    @MainActor
    private func load() async {
        do {
            let reports = try await fetchFoodItemReports()
            groups = await Self.grouped(reports, fetchFoodItemByBarcode: fetchFoodItemByBarcode)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    private static func grouped(
        _ reports: [FoodItemReportDomain],
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) async -> [ReportGroup] {
        let barcodes = Array(Set(reports.map(\.barcode)))
        let items = (try? await fetchFoodItemByBarcode(barcodes: barcodes)) ?? []
        let namesByBarcode = Dictionary(items.map { ($0.id, $0.displayName) }) { first, _ in first }
        let reportsByBarcode = Dictionary(grouping: reports, by: \.barcode)
        return reportsByBarcode
            .map { barcode, reports in
                ReportGroup(
                    barcode: barcode,
                    itemName: namesByBarcode[barcode],
                    reports: reports.sorted { $0.reportedAt > $1.reportedAt }
                )
            }
            .sorted { lhs, rhs in
                if lhs.reports.count != rhs.reports.count { return lhs.reports.count > rhs.reports.count }
                let lhsLatest = lhs.reports.first?.reportedAt ?? .distantPast
                let rhsLatest = rhs.reports.first?.reportedAt ?? .distantPast
                return lhsLatest > rhsLatest
            }
    }
}
