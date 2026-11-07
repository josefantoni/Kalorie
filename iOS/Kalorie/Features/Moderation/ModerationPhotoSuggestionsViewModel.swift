//
//  ModerationPhotoSuggestionsViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

final class ModerationPhotoSuggestionsViewModel: ObservableObject {

    // MARK: - Types

    struct Row: Identifiable {
        let suggestion: PhotoSuggestionDomain
        let itemName: String?

        var id: String { suggestion.id }
    }

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var rows: [Row] = []
    @Published var alertItem: AlertItem?

    private let fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol
    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol

    // MARK: - Init

    init(
        fetchPhotoSuggestions: any FetchPhotoSuggestionsUseCaseProtocol,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    ) {
        self.fetchPhotoSuggestions = fetchPhotoSuggestions
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        state = .loading
        defer { state = .loaded }
        await load()
    }

    @MainActor
    func onRefresh() async {
        await load()
    }

    @MainActor
    func onSuggestionResolved(id: String) {
        rows.removeAll { $0.id == id }
    }

    // MARK: - Private

    @MainActor
    private func load() async {
        do {
            let suggestions = try await fetchPhotoSuggestions()
            let items = (try? await fetchFoodItemByBarcode(barcodes: Array(Set(suggestions.map(\.itemId))))) ?? []
            let namesById = Dictionary(items.map { ($0.id, $0.displayName) }) { first, _ in first }
            rows = suggestions.sorted { $0.suggestedAt < $1.suggestedAt }.map { Row(suggestion: $0, itemName: namesById[$0.itemId]) }
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
