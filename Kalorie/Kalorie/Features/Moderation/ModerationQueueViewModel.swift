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
        collidingBarcodes.contains(submission.barcode)
    }

    // MARK: - Private

    @MainActor
    private func refreshCollisions() async {
        let barcodes = submissions.map(\.barcode)
        collidingBarcodes = await Self.collidingBarcodes(among: barcodes, fetchFoodItemByBarcode: fetchFoodItemByBarcode)
    }

    private static func collidingBarcodes(
        among barcodes: [String],
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) async -> Set<String> {
        await withTaskGroup(of: (barcode: String, exists: Bool).self) { group in
            for barcode in barcodes {
                group.addTask {
                    let exists = (try? await fetchFoodItemByBarcode(barcode: barcode)) != nil
                    return (barcode, exists)
                }
            }
            var colliding: Set<String> = []
            for await result in group where result.exists {
                colliding.insert(result.barcode)
            }
            return colliding
        }
    }
}
