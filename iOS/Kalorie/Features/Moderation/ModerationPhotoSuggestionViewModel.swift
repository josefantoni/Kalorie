//
//  ModerationPhotoSuggestionViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation

final class ModerationPhotoSuggestionViewModel: ObservableObject {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .idle
    @Published private(set) var itemName: String?
    @Published private(set) var canApprove = true
    @Published private(set) var shouldDismiss = false
    @Published var alertItem: AlertItem?

    let suggestion: PhotoSuggestionDomain

    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let approvePhotoSuggestion: any ApprovePhotoSuggestionUseCaseProtocol
    private let rejectPhotoSuggestion: any RejectPhotoSuggestionUseCaseProtocol
    private let onResolved: () -> Void

    // MARK: - Init

    init(
        suggestion: PhotoSuggestionDomain,
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        approvePhotoSuggestion: any ApprovePhotoSuggestionUseCaseProtocol,
        rejectPhotoSuggestion: any RejectPhotoSuggestionUseCaseProtocol,
        onResolved: @escaping () -> Void
    ) {
        self.suggestion = suggestion
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.approvePhotoSuggestion = approvePhotoSuggestion
        self.rejectPhotoSuggestion = rejectPhotoSuggestion
        self.onResolved = onResolved
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        do {
            let item = try await fetchFoodItemByBarcode(barcode: suggestion.itemId)
            itemName = item?.displayName
            if item == nil || item?.photoURL != nil {
                blockApproval(item == nil ? L10n.Moderation.photoSuggestionItemMissing : L10n.Moderation.photoSuggestionAlreadyHasPhoto)
            }
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
        }
    }

    @MainActor
    func onApproveTapped() async {
        state = .loading
        defer { state = .loaded }
        do {
            try await approvePhotoSuggestion(suggestion)
            onResolved()
            shouldDismiss = true
        } catch ApprovePhotoSuggestionError.itemAlreadyHasPhoto {
            blockApproval(L10n.Moderation.photoSuggestionAlreadyHasPhoto)
        } catch ApprovePhotoSuggestionError.itemMissing {
            blockApproval(L10n.Moderation.photoSuggestionItemMissing)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onRejectTapped() async {
        state = .loading
        defer { state = .loaded }
        do {
            try await rejectPhotoSuggestion(suggestion)
            onResolved()
            shouldDismiss = true
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    // MARK: - Private

    @MainActor
    private func blockApproval(_ message: String) {
        canApprove = false
        alertItem = AlertItem(title: message)
    }
}
