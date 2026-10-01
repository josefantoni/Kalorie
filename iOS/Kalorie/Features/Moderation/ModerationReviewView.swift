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
                        .fontWeight(.medium)
                }
            }
            FoodItemFormSections(
                formInput: $viewModel.formInput,
                highlightedFields: viewModel.recognizedFields,
                barcodeRow: .locked,
                showsAlcoholByVolumeField: true,
                onNutritionLabelScanTapped: {
                    Task { await viewModel.onNutritionLabelCameraTapped() }
                }
            ) { field in
                viewModel.onFormFieldEdited(field)
            }
            if viewModel.showsSimilarCatalogueItemsSection {
                similarCatalogueItemsSection
            }
            approveButtonSection
            rejectButtonSection
        }
        .task { await viewModel.onAppear() }
        .navigationTitle(viewModel.formInput.name)
        .navigationBarTitleDisplayMode(.inline)
        .keyboardDoneToolbar()
        .loader(viewModel.state.isLoading)
        .fullScreenCover(isPresented: $viewModel.isNutritionLabelCameraVisible) {
            NutritionLabelCameraView(
                isRecognizing: viewModel.isRecognizingNutritionLabel,
                hint: viewModel.nutritionLabelCameraHint,
                onCaptured: { image, liveBarcode in
                    await viewModel.onNutritionLabelCaptured(image, liveBarcode: liveBarcode)
                },
                onClose: { viewModel.isNutritionLabelCameraVisible = false }
            )
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

    // MARK: - Functions

    private var approveButtonSection: some View {
        Section {
            Button {
                Task { await viewModel.onApproveTapped() }
            } label: {
                Text(L10n.Moderation.buttonApprove)
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
    }

    private var rejectButtonSection: some View {
        Section {
            Button(role: .destructive) {
                viewModel.isRejectSheetVisible = true
            } label: {
                Text(L10n.Moderation.buttonReject)
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .tint(.red)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        .listSectionSpacing(.custom(8))
    }

    @ViewBuilder private var similarCatalogueItemsSection: some View {
        if viewModel.isSimilarCatalogueItemsSectionAvailable {
            Section(header: Text(L10n.Moderation.similarItemsSectionTitle)) {
                if viewModel.similarCatalogueItems.isEmpty {
                    Text(L10n.Moderation.similarItemsEmpty)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.similarCatalogueItems, id: \.id) { item in
                        VStack(alignment: .leading) {
                            Text(item.displayName)
                            Text("\(Int(item.caloriesPerHundredGrams)) kcal / 100 \(item.measure.unitSymbol)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
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
                    rejectReason: "nějaký text",
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
                rejectSubmission: RejectSubmissionUseCaseFake(),
                searchFoodItems: SearchFoodItemsUseCaseFake(),
                recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(),
                cameraAuthorizationProvider: CameraAuthorizationProviderFake()
            ) {}
        )
    }
}
