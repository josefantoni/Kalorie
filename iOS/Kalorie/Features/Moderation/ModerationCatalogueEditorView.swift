//
//  ModerationCatalogueEditorView.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import SwiftUI

struct ModerationCatalogueEditorView: View {

    // MARK: - Properties

    @StateObject var viewModel: ModerationCatalogueEditorViewModel
    @Environment(\.dismiss) private var dismiss

    // MARK: - Init

    init(viewModel: ModerationCatalogueEditorViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        List {
            if !viewModel.reports.isEmpty {
                Section(L10n.Moderation.reportsTitle) {
                    ForEach(viewModel.reports, id: \.reportedBy) { report in
                        Text(report.reason)
                            .fontWeight(.medium)
                    }
                }
            }
            if !viewModel.isOpenedFromReport {
                Section {
                    FloatingLabelTextField(
                        title: L10n.Moderation.editorSearchPlaceholder,
                        text: $viewModel.barcodeQuery,
                        keyboardType: .numberPad,
                        trailingButton: BadgeButton(imageName: .barCode) {
                            Task { await viewModel.onSearchTapped() }
                        }
                    )
                    .listRowInsets(.vertical, 0)
                }
            }
            if viewModel.loadedItem != nil {
                FoodItemFormSections(
                    formInput: $viewModel.formInput,
                    highlightedFields: viewModel.recognizedFields,
                    barcodeRow: viewModel.isOpenedFromReport ? .locked : .hidden,
                    showsAlcoholByVolumeField: true,
                    onNutritionLabelScanTapped: viewModel.isOpenedFromReport ? nil : {
                        Task { await viewModel.onNutritionLabelCameraTapped() }
                    }
                ) { field in
                    viewModel.onFormFieldEdited(field)
                }
            }
            if viewModel.isOpenedFromReport {
                saveAndResolveButtonSection
                rejectButtonSection
            }
        }
        .navigationTitle(L10n.Moderation.editorTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !viewModel.isOpenedFromReport {
                ToolbarItem(placement: .topBarTrailing) {
                    SaveToolbarButton(
                        title: L10n.Moderation.buttonSave,
                        showCheckmark: viewModel.showCheckmark,
                        isEnabled: viewModel.loadedItem != nil && !viewModel.state.isLoading
                    ) {
                        Task { await viewModel.onSaveTapped() }
                    }
                }
            }
        }
        .keyboardDoneToolbar()
        .loader(viewModel.state.isLoading)
        .task { await viewModel.onAppear() }
        .fullScreenCover(isPresented: $viewModel.isNutritionLabelCameraVisible) {
            NutritionLabelCameraView(
                isRecognizing: viewModel.isRecognizingNutritionLabel,
                hint: viewModel.nutritionLabelCameraHint,
                onRecognized: { reading, ocrText, liveBarcode in
                    await viewModel.onNutritionLabelRecognized(reading, ocrText: ocrText, liveBarcode: liveBarcode)
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
        .onChange(of: viewModel.shouldDismiss) {
            if viewModel.shouldDismiss { dismiss() }
        }
    }

    // MARK: - Functions

    private var saveAndResolveButtonSection: some View {
        Section {
            PrimaryButton(L10n.Moderation.buttonSaveAndResolve) {
                Task { await viewModel.onSaveAndResolveTapped() }
            }
            .disabled(viewModel.loadedItem == nil || viewModel.state.isLoading)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
    }

    private var rejectButtonSection: some View {
        Section {
            PrimaryButton(L10n.Moderation.buttonReject, style: .destructive) {
                Task { await viewModel.onRejectTapped() }
            }
            .disabled(viewModel.state.isLoading)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        .listSectionSpacing(.custom(8))
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationCatalogueEditorView(
            viewModel: ModerationCatalogueEditorViewModel(
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                updateFoodItem: UpdateFoodItemUseCaseFake(),
                deleteFoodItemReport: DeleteFoodItemReportUseCaseFake(),
                modelExtractor: NutritionLabelModelExtractorFake(),
                cameraAuthorizationProvider: CameraAuthorizationProviderFake()
            )
        )
    }
}
#endif
