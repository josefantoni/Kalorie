//
//  ModerationQueueViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

final class ModerationQueueViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var submissions: [FoodItemSubmissionDomain] = []
    @Published private(set) var collidingBarcodes: Set<String> = []
    @Published var alertItem: AlertItem?

    private let fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol

    // MARK: - Init

    init(
        fetchPendingSubmissions: any FetchPendingSubmissionsUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) {
        self.fetchPendingSubmissions = fetchPendingSubmissions
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        state = .loading
        defer { state = .loaded }
        do {
            submissions = try await fetchPendingSubmissions()
            await refreshCollisions()
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
    func onSubmissionResolved(id: String) async {
        submissions.removeAll { $0.id == id }
        await refreshCollisions()
    }

    func isColliding(_ submission: FoodItemSubmissionDomain) -> Bool {
        guard let barcode = submission.barcode else { return false }
        return collidingBarcodes.contains(barcode)
    }

    // MARK: - Private

    @MainActor
    private func refreshCollisions() async {
        let barcodes = submissions.compactMap(\.barcode)
        collidingBarcodes = await Self.collidingBarcodes(among: barcodes, fetchFoodItemByBarcode: fetchFoodItemByBarcode)
    }

    private static func collidingBarcodes(
        among barcodes: [String],
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) async -> Set<String> {
        let existing = (try? await fetchFoodItemByBarcode(barcodes: barcodes)) ?? []
        return Set(existing.map(\.id))
    }
}
