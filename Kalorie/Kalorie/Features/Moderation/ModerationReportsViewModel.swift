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
    private let deleteFoodItemReport: any DeleteFoodItemReportUseCaseProtocol

    // MARK: - Init

    init(
        fetchFoodItemReports: any FetchFoodItemReportsUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        deleteFoodItemReport: any DeleteFoodItemReportUseCaseProtocol
    ) {
        self.fetchFoodItemReports = fetchFoodItemReports
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.deleteFoodItemReport = deleteFoodItemReport
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        state = .loading
        defer { state = .loaded }
        do {
            let reports = try await fetchFoodItemReports()
            groups = await Self.grouped(reports, fetchFoodItemByBarcode: fetchFoodItemByBarcode)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onRefresh() async {
        await onAppear()
    }

    @MainActor
    func onResolveTapped(_ group: ReportGroup) async {
        var remainingReports: [FoodItemReportDomain] = []
        for report in group.reports {
            do {
                try await deleteFoodItemReport(barcode: report.barcode, reportedBy: report.reportedBy)
            } catch {
                Log.error(error, category: Constants.LogCategory.moderation)
                remainingReports.append(report)
            }
        }
        guard !remainingReports.isEmpty else {
            groups.removeAll { $0.barcode == group.barcode }
            return
        }
        if let index = groups.firstIndex(where: { $0.barcode == group.barcode }) {
            groups[index] = ReportGroup(barcode: group.barcode, itemName: group.itemName, reports: remainingReports)
        }
        alertItem = AlertItem(title: L10n.Common.errorUnknown)
    }

    // MARK: - Private

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
