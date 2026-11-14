//
//  ModerationReportsViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

final class ModerationReportsViewModel: ObservableObject {

    // MARK: - Types

    struct Row: Identifiable {
        let report: FoodItemReportDomain
        let itemName: String?

        var id: String { report.id }
    }

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var rows: [Row] = []
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
    func onReportResolved(id: String) {
        rows.removeAll { $0.id == id }
    }

    // MARK: - Private

    @MainActor
    private func load() async {
        do {
            let reports = try await fetchFoodItemReports()
            let items = (try? await fetchFoodItemByBarcode(barcodes: Array(Set(reports.map(\.barcode))))) ?? []
            let namesByBarcode = Dictionary(items.map { ($0.id, $0.displayName) }) { first, _ in first }
            rows = reports.map { Row(report: $0, itemName: namesByBarcode[$0.barcode]) }
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
