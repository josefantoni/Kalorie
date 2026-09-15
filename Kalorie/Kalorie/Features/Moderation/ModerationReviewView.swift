//
//  ModerationReviewView.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import SwiftUI

struct ModerationReviewView: View {

    // MARK: - Properties

    @StateObject var viewModel: ModerationReviewViewModel
    @Environment(\.dismiss) private var dismiss

    // MARK: - Init

    init(viewModel: ModerationReviewViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        List {
            if let rejectReason = viewModel.rejectReasonIfAny {
                Section {
                    Text(L10n.AddFood.submissionRejectedReason(reason: rejectReason))
                        .foregroundStyle(.red)
                }
            }
            Section {
                BaseStringTextField(
                    placeholder: L10n.AddFood.fieldBarcodePlaceholder,
                    title: L10n.AddFood.fieldBarcodeTitle,
                    text: .constant(viewModel.formInput.scannedCode)
                )
                .disabled(true)
                FoodItemFormFields(formInput: $viewModel.formInput)
            }
            FoodPortionsSection(portions: $viewModel.formInput.portions)
        }
        .navigationTitle(viewModel.formInput.name)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(L10n.Moderation.buttonApprove) {
                    Task { await viewModel.onApproveTapped() }
                }
            }
            ToolbarItem(placement: .bottomBar) {
                Button(L10n.Moderation.buttonReject, role: .destructive) {
                    viewModel.isRejectSheetVisible = true
                }
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .alert(L10n.Moderation.buttonReject, isPresented: $viewModel.isRejectSheetVisible) {
            TextField(L10n.Moderation.rejectReasonPlaceholder, text: $viewModel.rejectReason)
            Button(L10n.Common.buttonCancel, role: .cancel) {}
            Button(L10n.Moderation.buttonReject, role: .destructive) {
                Task { await viewModel.onRejectConfirmed() }
            }
        }
        .onChange(of: viewModel.shouldDismiss) {
            if viewModel.shouldDismiss { dismiss() }
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationReviewView(
            viewModel: ModerationReviewViewModel(
                submission: FoodItemSubmissionDomain(
                    id: "sub-1",
                    barcode: "12345678",
                    submittedBy: "user-1",
                    status: .pending,
                    submittedAt: .now,
                    rejectReason: nil,
                    item: FoodItemDomain(
                        id: "12345678",
                        kind: .catalogue,
                        czName: "Tvaroh",
                        engName: "Cottage cheese",
                        weight: 200,
                        date: .now,
                        energyKJ: 335,
                        caloriesPerHundredGrams: 80,
                        fat: 0.5,
                        fatSaturated: 0.3,
                        fatUnsaturatedFattyAcids: 0.2,
                        carbohydrate: 4,
                        carbohydratePureSugar: 3,
                        fiber: 0,
                        protein: 13,
                        salt: 0.1
                    )
                ),
                approveSubmission: ApproveSubmissionUseCaseFake(),
                rejectSubmission: RejectSubmissionUseCaseFake()
            ) {}
        )
    }
}
