//
//  ModerationReviewViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

final class ModerationReviewViewModel: ObservableObject {

    // MARK: - Properties

    @Published var formInput: FoodItemFormInput
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published var isRejectSheetVisible = false
    @Published var rejectReason = ""
    @Published private(set) var shouldDismiss = false

    let submittedAt: Date
    let rejectReasonIfAny: String?

    private let submission: FoodItemSubmissionDomain
    private let approveSubmission: any ApproveSubmissionUseCaseProtocol
    private let rejectSubmission: any RejectSubmissionUseCaseProtocol
    private let onResolved: () -> Void

    // MARK: - Init

    init(
        submission: FoodItemSubmissionDomain,
        approveSubmission: any ApproveSubmissionUseCaseProtocol,
        rejectSubmission: any RejectSubmissionUseCaseProtocol,
        onResolved: @escaping () -> Void
    ) {
        self.submission = submission
        self.formInput = FoodItemFormInput(item: submission.item)
        self.submittedAt = submission.submittedAt
        self.rejectReasonIfAny = submission.rejectReason
        self.approveSubmission = approveSubmission
        self.rejectSubmission = rejectSubmission
        self.onResolved = onResolved
    }

    // MARK: - Functions

    @MainActor
    func onApproveTapped() async {
        state = .loading
        defer { state = .loaded }
        let editedItem = formInput.asFoodItemDomain(date: submission.item.date)
        do {
            try await approveSubmission(id: submission.id, item: editedItem)
            onResolved()
            shouldDismiss = true
        } catch CreateFoodItemError.itemAlreadyExists {
            alertItem = AlertItem(title: L10n.Moderation.errorAlreadyExists)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onRejectConfirmed() async {
        state = .loading
        defer { state = .loaded }
        do {
            try await rejectSubmission(submission, reason: rejectReason)
            onResolved()
            shouldDismiss = true
        } catch RejectSubmissionError.reasonRequired {
            alertItem = AlertItem(title: L10n.Moderation.errorReasonRequired)
        } catch RejectSubmissionError.alreadyResolved {
            onResolved()
            alertItem = AlertItem(title: L10n.Moderation.errorAlreadyResolved)
            shouldDismiss = true
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
