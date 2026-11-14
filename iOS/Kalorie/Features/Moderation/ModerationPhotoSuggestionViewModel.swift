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
    @Published var responseDraft: ModerationResponseDraft?
    @Published private(set) var isSendingResponse = false
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
            if item == nil {
                blockApproval(L10n.Moderation.photoSuggestionItemMissing)
            }
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
        }
    }

    @MainActor
    func onApproveTapped() {
        guard canApprove, !isSendingResponse else { return }
        responseDraft = ModerationResponseDraft(
            status: .accepted,
            text: L10n.Moderation.responsePhotoAccepted(locale: suggestion.locale)
        )
    }

    @MainActor
    func onRejectTapped() {
        guard !isSendingResponse else { return }
        responseDraft = ModerationResponseDraft(status: .rejected, text: "")
    }

    @MainActor
    func onResponseSendTapped() async {
        guard
            let draft = responseDraft,
            draft.isSendable,
            !isSendingResponse
        else { return }
        isSendingResponse = true
        defer { isSendingResponse = false }
        do {
            switch draft.status {
            case .accepted:
                try await approvePhotoSuggestion(suggestion, response: draft.text)
            case .rejected, .pending:
                try await rejectPhotoSuggestion(suggestion, response: draft.text)
            }
        } catch ApprovePhotoSuggestionError.itemMissing {
            responseDraft = nil
            blockApproval(L10n.Moderation.photoSuggestionItemMissing)
            return
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            responseDraft?.errorTitle = L10n.Common.errorUnknown
            return
        }
        responseDraft = nil
        onResolved()
        shouldDismiss = true
    }

    // MARK: - Private

    @MainActor
    private func blockApproval(_ message: String) {
        canApprove = false
        alertItem = AlertItem(title: message)
    }
}
