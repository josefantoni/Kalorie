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

    // MARK: - Init

    init(viewModel: ModerationCatalogueEditorViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        List {
            Section {
                HStack {
                    TextField(L10n.Moderation.editorSearchPlaceholder, text: $viewModel.barcodeQuery)
                        .keyboardType(.numberPad)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .medium
                    ) {
                        Task { await viewModel.onSearchTapped() }
                    }
                }
            }
            if viewModel.loadedItem != nil {
                FoodItemFormSections(
                    formInput: $viewModel.formInput,
                    highlightedFields: viewModel.recognizedFields,
                    onNutritionLabelScanTapped: {
                        Task { await viewModel.onNutritionLabelCameraTapped() }
                    }
                ) { field in
                    viewModel.onFormFieldEdited(field)
                }
                Section {
                    Button {
                        Task { await viewModel.onSaveTapped() }
                    } label: {
                        HStack {
                            Text(L10n.Moderation.buttonSave)
                            if viewModel.didSave {
                                Spacer()
                                Text(L10n.Moderation.editorSaved)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(L10n.Moderation.editorTitle)
        .navigationBarTitleDisplayMode(.inline)
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
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationCatalogueEditorView(
            viewModel: ModerationCatalogueEditorViewModel(
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                updateFoodItem: UpdateFoodItemUseCaseFake(),
                recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(),
                cameraAuthorizationProvider: CameraAuthorizationProviderFake()
            )
        )
    }
}
